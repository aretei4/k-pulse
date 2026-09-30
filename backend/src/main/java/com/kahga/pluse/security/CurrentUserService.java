package com.kahga.pluse.security;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** One place to answer "who is calling?" — every service asks here, never the filter chain. */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public UserPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException("Sign in to continue", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }

    /** The live row, so a deactivated agent loses access on their next request, not at token expiry. */
    public User user() {
        return userRepository
                .findById(principal().id())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException("This account is no longer active", HttpStatus.FORBIDDEN));
    }
}
