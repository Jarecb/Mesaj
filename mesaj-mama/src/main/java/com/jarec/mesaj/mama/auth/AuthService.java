package com.jarec.mesaj.mama.auth;

import com.jarec.mesaj.common.protocol.Login;
import com.jarec.mesaj.common.protocol.LoginSuccess;
import com.jarec.mesaj.common.protocol.UserList;

import java.util.UUID;

public final class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("userRepository must not be null");
        }
        this.userRepository = userRepository;
    }

    public LoginSuccess login(Login login) {
        if (login == null) {
            throw new IllegalArgumentException("login must not be null");
        }
        User user = userRepository.findOrCreate(login.username());
        return new LoginSuccess(user.username(), user.permanentUuid().toString(), UUID.randomUUID().toString());
    }

    public UserList getUsers() {
        return new UserList(userRepository.listUsers());
    }
}
