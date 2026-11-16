package com.jarec.mesaj.mama.auth;

import java.util.Map;

public interface UserRepository {
    User findOrCreate(String username);

    Map<String, String> listUsers();
}
