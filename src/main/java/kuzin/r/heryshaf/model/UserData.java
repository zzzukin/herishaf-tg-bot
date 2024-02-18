package kuzin.r.heryshaf.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Data
@Entity
@Table(name="user_data")
public class UserData {

    @Id
    Long chatId;

    String firstName;

    String lastName;

    String name;

    Date registerDate;

    Double longitude;

    Double latitude;
}
