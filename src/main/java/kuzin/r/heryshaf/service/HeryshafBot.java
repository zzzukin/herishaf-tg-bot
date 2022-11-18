package kuzin.r.heryshaf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kuzin.r.heryshaf.config.BotConfig;
import kuzin.r.heryshaf.consts.Emoji;
import kuzin.r.heryshaf.consts.Expectation;
import kuzin.r.heryshaf.consts.Phrase;
import kuzin.r.heryshaf.consts.Result;
import kuzin.r.heryshaf.model.*;
import kuzin.r.heryshaf.repository.UserRepository;
import kuzin.r.heryshaf.repository.WeatherRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static kuzin.r.heryshaf.consts.Expectation.*;

@Slf4j
@Component
@EnableScheduling
public class HeryshafBot extends TelegramLongPollingBot {

    @Autowired
    WeatherRepository weatherRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WeatherService weatherService;
    @Autowired
    WaterLevelService waterLevelService;

    private final BotConfig config;
    private WeatherData lastSavedData = new WeatherData();
    private Expectation expectation = NOTHING;

    @Autowired
    public HeryshafBot(BotConfig config) throws TelegramApiException {
        this.config = config;
        List<BotCommand> botCommands = new ArrayList<>();
        botCommands.add(new BotCommand("/help", "помощь"));
        botCommands.add(new BotCommand("/start", "спрогнозировать доброту рек"));
        botCommands.add(new BotCommand("/weather", "узнать погоду"));
        botCommands.add(new BotCommand("/result", "рассказать о результатах"));
        botCommands.add(new BotCommand("/about", "узнать обо мне"));
        execute(new SetMyCommands(botCommands, new BotCommandScopeDefault(), null));
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {

        Message message = update.getMessage();

        if (update.hasMessage() && update.getMessage().hasText()) {

            switch (message.getText()) {
                case "/start":
                    startCommandHandler(message);
                    break;
                case "/help":
                    helpCommandHandler(message);
                    break;
                case "/weather":
                    weatherCommandHandler(message);
                    break;
                case "/result":
                    resultCommandHandler(message);
                    break;
                case "/about":
                    aboutCommandHandler(message);
                    break;
                default:
                    phraseHandler(message);
            }
        }

        if (expectation.equals(LOCATION)) {
            if (update.hasMessage() && message.getLocation() != null) {
                resultLocationHandler(message);
            }
        }

        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            resultCallbackHandler(callbackQuery);
            if (!expectation.equals(NOTHING)) {
                return;
            }
        }

        expectation = NOTHING;
    }

    private void resultCallbackHandler(CallbackQuery callbackQuery) throws TelegramApiException, IOException {
        String callbackQueryData = callbackQuery.getData();
        for (Result result : Result.values()) {
            if (callbackQueryData.equals(result.name())) {
                EditMessageText editMessageText = getEditMessageText(callbackQuery.getMessage());
                editMessageText.setText(String.format("Твой результат сегодня - %s! " +
                                "Спасибо тебе мой друг за ответ. " +
                                "Теперь укажи мне, пожалуйста, местоположение%s (нажми %s " +
                                "снизу справа и выбери 'location')",
                        result.getText(),
                        Emoji.WINKING_FACE,
                        Emoji.PAPERCLIP
                ));

                updateData(getWeatherData());
                WeatherData data = loadData();
                data.setResult(result.getText());
                String author = callbackQuery.getMessage().getChat().getFirstName();
                data.setResultAuthor(author);
                log.info("Add result author: {}", author);
                saveData(data);

                execute(editMessageText);
                expectation = LOCATION;
                break;
            }
        }
    }

    private void resultLocationHandler(Message message) throws TelegramApiException {
        Location location = message.getLocation();
        WeatherData data = loadData();
        ResultLocation resultLocation = new ResultLocation(location.getLongitude(), location.getLongitude());
        data.setResultLocation(resultLocation);
        log.info("Add result location: {}", resultLocation);
        saveData(data);

        SendMessage sendMessage = getSendMessage(message.getChatId());
        sendMessage.setText(String.format("Хорошо, %s, я запомнил%s",
                message.getChat().getFirstName(),
                Emoji.WINKING_FACE
        ));

        execute(sendMessage);
    }

    //@Scheduled(cron = "* */${bot.update.data.time} * * * *", zone = "Europe/Moscow")
    @Scheduled(fixedDelay = 1000 * 60 * 60)
    public void updateDataBySchedule() throws IOException {
        log.info("Update data by scheduler {}", new Date());
        updateData(getWeatherData());
    }

    //      +--------------------sec (0 - 59)
    //      |  +---------------- minute (0 - 59)
    //      |  |  +------------- hour (0 - 23)
    //      |  |  |  +---------- day of month (1 - 31)
    //      |  |  |  |  +------- month (1 - 12)
    //      |  |  |  |  |  +---- day of week (0 - 6) (Sunday=0 or 7)
    //      |  |  |  |  |  |
    //      *  *  *  *  *  *  command to be executed
    @Scheduled(cron = "${bot.reminder.mes.date}", zone = "Europe/Moscow")
    private void sendReminderMessage() throws TelegramApiException {
        Iterable<UserData> users = userRepository.findAll();
        for (UserData user : users) {
            SendMessage sendMessage = getSendMessage(user.getChatId());
            sendMessage.setText(String.format("Привет, как дела? Поедешь на рыбалку завтра? %s%s",
                    Emoji.SLIGHTLY_SMILING_FACE,
                    Emoji.FISH
            ));

            execute(sendMessage);
            expectation = FISHING;
        }
    }

    private void updateData(WeatherData data) {
        if (!data.getOpenWeatherMap().equals(lastSavedData.getOpenWeatherMap())
                || !data.getWaterLevel().equals(lastSavedData.getWaterLevel())
                || !data.getResultAuthor().equals(lastSavedData.getResultAuthor())
                || !data.getResultLocation().equals(lastSavedData.getResultLocation())
                || !data.getResult().equals(lastSavedData.getResult())
        ) {
            lastSavedData = saveData(data);
        }
    }

    @Transactional
    private WeatherData getWeatherData() throws IOException {
        log.info("Get weather data from: {}", weatherService.getResource());
        JSONObject json = weatherService.getWeather();
        log.info("Weather data: {}", json);
        ObjectMapper mapper = new ObjectMapper();
        OpenWeatherMap openWeatherMap = mapper.readValue(json.toString(), OpenWeatherMap.class);

        log.info("Get water level from: {}", waterLevelService.getResource());
        WaterLevel waterLevel = waterLevelService.getWaterLevel();
        log.info("Received water level: {}({})", waterLevel.getLevel(), waterLevel.getDiff());

        WeatherData data = new WeatherData();
        data.setTimestamp(new Date().getTime());
        data.setOpenWeatherMap(openWeatherMap);
        data.setWaterLevel(waterLevel);

        return data;
    }

    private WeatherData saveData(WeatherData data) {
        log.info("Update data");
        weatherRepository.save(data);

        if (weatherRepository.count() > config.getDbRecordsNum()) {
            long timestamp = weatherRepository.findTopByOrderByTimestampAsc().getTimestamp();
            weatherRepository.deleteByTimestamp(timestamp);
        }

        return data;
    }

    private WeatherData loadData() {
        return weatherRepository.findTopByOrderByTimestampDesc();
    }

    private SendMessage getSendMessage(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        return sendMessage;
    }

    private EditMessageText getEditMessageText(Message message) {
        Long chatId = message.getChatId();
        Integer messageId = message.getMessageId();
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(String.valueOf(chatId));
        editMessageText.setMessageId(messageId);
        return editMessageText;
    }

    private void helpCommandHandler(Message message) throws TelegramApiException {
        SendMessage sendMessage = getSendMessage(message.getChatId());
        sendMessage.setText(String.format("%s, я с удовольствием поделюсь с " +
                        "тобой своими знаниями%s\n\n" +
                        "<b>Используй, пожалуйста:</b>\n" +
                        "/start - спрогнозировать доброту рек\n" +
                        "/weather - узнать погоду\n" +
                        "/result - рассказать о результатах\n" +
                        "/about - узнать обо мне\n\n" +
                        "Кстати, видишь там в нижнем левом углу синяя " +
                        "кнопка меню? Она поможет тебе%s",
                message.getChat().getFirstName(),
                Emoji.SLIGHTLY_SMILING_FACE,
                Emoji.SMIRKING_FACE
        ));
        sendMessage.setParseMode(ParseMode.HTML);

        execute(sendMessage);
    }

    private void startCommandHandler(Message message) throws TelegramApiException {
        registerUser(message.getChat());
        SendMessage sendMessage = getSendMessage(message.getChatId());
        sendMessage.setText(String.format("Куааа... куааа... куааа...%s%s%s " +
                        "В общем есть небольшое обстоятельство. " +
                        "На данный момент Дмитрий Кузин @da_kuzin%s " +
                        "все еще обучает меня делать предсказания. Нейронка там..." +
                        "туда, сюда... Но я уверен, что скоро научусь этому и обязательно " +
                        "дам тебе знать. Мы обязательно еще это отметим%s%s " +
                        "А пока, смотри, что я умею: /help",
                Emoji.CLOWN_FACE,
                Emoji.CLOWN_FACE,
                Emoji.MAN_SHRUGGING,
                Emoji.SMIRKING_FACE,
                Emoji.CLINKING_GLASSES,
                Emoji.PARTYING_FACE
        ));

        execute(sendMessage);
    }

    private void registerUser(Chat chat) {
        if (!userRepository.findById(chat.getId()).isPresent()) {
            UserData userData = new UserData();
            userData.setChatId(chat.getId());
            userData.setFirstName(chat.getFirstName());
            userData.setLastName(chat.getLastName());
            userData.setName(chat.getUserName());
            userData.setRegisterDate(new Date());

            log.info("New user registered: {}", userData);
            userRepository.save(userData);
        }
    }

    private void weatherCommandHandler(Message message) throws TelegramApiException {
        WeatherData data = loadData();
        OpenWeatherMap openWeatherMap = data.getOpenWeatherMap();
        WaterLevel waterLevel = data.getWaterLevel();
        SendMessage sendMessage = getSendMessage(message.getChatId());
        sendMessage.setText(String.format("А погода нынче такая, %s %s\n\n" +
                        "<b>Страна:</b> %s\n" +
                        "<b>Город:</b> %s\n" +
                        "<b>Температура:</b> %s C\n " +
                        "\t(<b>ощущается как:</b> %s C)\n" +
                        "<b>Влажность:</b> %s%%\n" +
                        "<b>Давление:</b> %s гПа\n" +
                        "<b>Направление ветра:</b> %s\n" +
                        "<b>Скорость ветра:</b> %s м/с\n" +
                        "<b>Уровень воды:</b> %s см(%s)\n",
                message.getChat().getFirstName(),
                Emoji.SUN_BEHIND_CLOUD,
                openWeatherMap.getSys().getCountry(),
                openWeatherMap.getName(),
                openWeatherMap.getMain().getTemp(),
                openWeatherMap.getMain().getFeelsLike(),
                openWeatherMap.getMain().getHumidity(),
                openWeatherMap.getMain().getPressure(),
                openWeatherMap.getWind().getDeg(),
                openWeatherMap.getWind().getSpeed(),
                waterLevel.getLevel(),
                waterLevel.getDiff()
        ));
        sendMessage.setParseMode(ParseMode.HTML);

        execute(sendMessage);
    }

    private void resultCommandHandler(Message message) throws TelegramApiException {
        SendMessage sendMessage = getSendMessage(message.getChatId());
        sendMessage.setText(String.format("И так, %s, расскажи, мне пожалуйста, " +
                        "насколько река была добра к тебе и плодородна сегодня?%s",
                message.getChat().getFirstName(),
                Emoji.THINKING_FACE
        ));

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttonsRows = new ArrayList<>();
        List<InlineKeyboardButton> buttonRow = new ArrayList<>();

        buttonRow.add(Result.UNSATISFACTORY.getButton());
        buttonRow.add(Result.BAD.getButton());
        buttonRow.add(Result.SATISFACTORY.getButton());
        buttonRow.add(Result.GOOD.getButton());
        buttonRow.add(Result.EXCELLENT.getButton());

        buttonsRows.add(buttonRow);
        keyboardMarkup.setKeyboard(buttonsRows);
        sendMessage.setReplyMarkup(keyboardMarkup);

        execute(sendMessage);
    }

    private void aboutCommandHandler(Message message) throws TelegramApiException {
        SendMessage sendMessage = getSendMessage(message.getChatId());
        sendMessage.setText(String.format("Привет%s я <a href='https://en.wikipedia.org/wiki/Heryshaf'>" +
                        "Херишеф</a> - древнеегипетский бог, покровитель Гераклеополя, бог плодородия " +
                        "и воды, покровитель охоты и рыболовства%s%s%s Кеххе, кеххе... да, так, вот. " +
                        "Я спал примерно 2000 лет пока меня не разбудил ммм... некто " +
                        "<a href='https://lah.ru/o-tsentre/sklyarov-a-yu/'>Андрей Скляров</a>%s%s%s " +
                        "Но похоже, что за время сна, я все еще не растерял своих навыков и могу предсказывать " +
                        "насколько реки будут плодородны и богаты рыбой. Ты можешь попросить " +
                        "меня об этом, командой /start. В обмен на свои предсказания, я хочу, " +
                        "чтобы ты делился со мной своми результатами /result - насколько река была " +
                        "добра к тебе и плодородна, ок?%s\n\n" +
                        "<b>P.S.</b> Кстати у мнея же есть создатель - @zzzukin\n" +
                        "Для любознательных он оставил информацию о том, как я устроен внутри:\n" +
                        "<b>github:</b> https://github.com/zzzukin/heryshaf-tg-bot.git\n" +
                        "А так же, информацию о том, что хранит мой разум (PostgreSQL):\n" +
                        "<b>Host:</b> ec2-52-49-201-212.eu-west-1.compute.amazonaws.com\n" +
                        "<b>Database:</b> d1tce789asg0h9\n" +
                        "<b>User:</b> ygnjwzavmyfxbb\n" +
                        "<b>Port:</b> 5432\n" +
                        "<b>Password:</b> 3853c8bf3c2b96abfc104f33c177dd9b85f68613e0187e36730c71304a0529e8"
                ,
                Emoji.WAVING_HAND,
                Emoji.SMILING_FACE_WITH_HALO,
                Emoji.FISH,
                Emoji.FISHING_POLE_AND_FISH,
                Emoji.DISGUISED_FACE,
                Emoji.RAGE,
                Emoji.FACE_WITH_SYMBOLS_ON_MOUTH,
                Emoji.SLIGHTLY_SMILING_FACE
        ));
        sendMessage.setParseMode(ParseMode.HTML);

        execute(sendMessage);
    }

    private void phraseHandler(Message message) throws TelegramApiException {
        SendMessage sendMessage = getSendMessage(message.getChatId());
        String phrase = message.getText().toLowerCase();

        if (expectation.equals(FISHING)) {
            if (Phrase.POSITIVE.get().contains(phrase)) {
                sendMessage.setText(String.format("Отлично, жду результатов%s%s%s",
                        Emoji.GRINNING_FACE,
                        Emoji.FISHING_POLE_AND_FISH,
                        Emoji.PARTYING_FACE
                ));
            } else if (Phrase.NEGATIVE.get().contains(phrase)) {
                sendMessage.setText(String.format("Печалька%s",
                        Emoji.CRYING_FACE
                ));
            }
        } else {
            sendMessage.setText(String.format("Привет, %s, %s %s?",
                    message.getChat().getFirstName(),
                    message.getText(),
                    Emoji.UPSIDE_DOWN_FACE));
        }

        execute(sendMessage);
    }
}
