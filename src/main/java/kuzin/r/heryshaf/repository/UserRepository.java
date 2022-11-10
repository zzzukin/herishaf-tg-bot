package kuzin.r.heryshaf.repository;

import kuzin.r.heryshaf.model.UserData;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<UserData, Long> {
}
