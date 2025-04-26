package dev.abu.screener_backend.annotations;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@PreAuthorize("hasPermission(null, 'SUBSCRIBED')")
public @interface SubscribedOnly {
}
