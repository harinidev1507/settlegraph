package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.UserSearchResponse;
import com.settlegraph.Artifacts.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSearchResponse> search(String query, Long excludeUserId) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return userRepository
                .findByUsernameContainingIgnoreCaseAndIdNot(query.trim(), excludeUserId, PageRequest.of(0, 10))
                .stream()
                .map(u -> new UserSearchResponse(u.getId(), u.getUsername(), u.getName()))
                .toList();
    }
}
