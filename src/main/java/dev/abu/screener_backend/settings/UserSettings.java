package dev.abu.screener_backend.settings;

import dev.abu.screener_backend.appuser.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class UserSettings {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private Settings settings;

    public UserSettings(AppUser appUser, Settings settings) {
        this.appUser = appUser;
        this.settings = settings;
    }
}
