package com.direitoria.questoes.auth.dto;

import com.direitoria.questoes.domain.Role;
import com.direitoria.questoes.domain.User;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        List<String> roles) {

    public static UserProfileResponse from(User u) {
        return new UserProfileResponse(
                u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getRoles().stream().map(Role::getAuthority).sorted().toList());
    }
}
