package kuzin.r.herishef.repository;

import kuzin.r.herishef.model.UserData;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<UserData, Long> {
}
