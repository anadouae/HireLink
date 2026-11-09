package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
