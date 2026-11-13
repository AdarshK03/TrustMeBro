package com.platform.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.userservice.dto.UserCreateRequest;
import com.platform.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_ValidPayload_Returns201AndPersistsData() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Diana Prince", "diana.prince@example.com", "ADMIN");

        mockMvc.perform(post("/api/users")
                        .header("X-Correlation-ID", "test-corr-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Diana Prince"))
                .andExpect(jsonPath("$.email").value("diana.prince@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.createdAt").exists());

        // Verify actual persistence in database
        assertTrue(userRepository.existsByEmail("diana.prince@example.com"));
    }

    @Test
    void createUser_DuplicateEmail_Returns409Conflict() throws Exception {
        UserCreateRequest first = new UserCreateRequest("Original User", "duplicate@example.com", "CUSTOMER");
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        UserCreateRequest duplicate = new UserCreateRequest("Duplicate User", "duplicate@example.com", "CUSTOMER");
        mockMvc.perform(post("/api/users")
                        .header("X-Correlation-ID", "test-corr-409")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("User with email duplicate@example.com already exists"))
                .andExpect(jsonPath("$.requestId").value("test-corr-409"));
    }

    @Test
    void createUser_BlankName_Returns400BadRequest() throws Exception {
        UserCreateRequest request = new UserCreateRequest("", "valid.email@example.com", "CUSTOMER");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Name is required")));
    }

    @Test
    void createUser_InvalidEmailFormat_Returns400BadRequest() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Valid Name", "not-a-valid-email", "CUSTOMER");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Email must be a valid email address")));
    }

    @Test
    void createUser_MalformedJson_Returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json-body}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Malformed request body")));
    }

    @Test
    void getUserById_ExistingUser_Returns200() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Bruce Wayne", "bruce.wayne@example.com", "CUSTOMER");
        String createResponse = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Bruce Wayne"))
                .andExpect(jsonPath("$.email").value("bruce.wayne@example.com"));
    }

    @Test
    void getUserById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/users/99999")
                        .header("X-Correlation-ID", "corr-404-test"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found with id: 99999"))
                .andExpect(jsonPath("$.requestId").value("corr-404-test"));
    }

    @Test
    void getAllUsers_Returns200WithList() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UserCreateRequest("User 1", "u1@example.com", "CUSTOMER"))));
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UserCreateRequest("User 2", "u2@example.com", "CUSTOMER"))));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
