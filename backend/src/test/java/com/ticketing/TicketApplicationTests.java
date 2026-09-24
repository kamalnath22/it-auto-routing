package com.ticketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.dto.AuthResponse;
import com.ticketing.dto.CreateTicketRequest;
import com.ticketing.dto.LoginRequest;
import com.ticketing.dto.RegisterRequest;
import com.ticketing.entity.Role;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.User;
import com.ticketing.repository.TicketRepository;
import com.ticketing.repository.UserRepository;
import com.ticketing.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(userRepository).isNotNull();
        assertThat(ticketRepository).isNotNull();
    }

    @Test
    @DisplayName("Valid registration creates an EMPLOYEE user with BCrypt hashed password")
    void testValidRegistration() throws Exception {
        String uniqueEmail = "reg_" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest("Kamal", uniqueEmail, "Password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.name").value("Kamal"))
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        User savedUser = userRepository.findByEmail(uniqueEmail).orElseThrow();
        assertThat(savedUser.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(savedUser.getPassword()).isNotEqualTo("Password123");
        assertThat(passwordEncoder.matches("Password123", savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Duplicate email registration is rejected with 409 Conflict")
    void testDuplicateEmailRejected() throws Exception {
        String uniqueEmail = "dup_" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest("First User", uniqueEmail, "Password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Attempting to register the same email again
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Invalid registration input (blank name, bad email, short password) is rejected with 400")
    void testInvalidRegistrationInputRejected() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest("", "not-an-email", "123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("Valid login succeeds and returns AuthResponse with JWT")
    void testValidLoginSucceeds() throws Exception {
        String uniqueEmail = "login_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest("Login User", uniqueEmail, "SecretPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(uniqueEmail, "SecretPass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("Invalid password during login returns 401 Unauthorized")
    void testInvalidPasswordRejected() throws Exception {
        String uniqueEmail = "wrongpass_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest("Test User", uniqueEmail, "CorrectPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(uniqueEmail, "WrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Unknown user login returns 401 Unauthorized without leaking user existence")
    void testUnknownUserRejected() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "SomePassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Protected ticket APIs reject requests with missing token (401)")
    void testMissingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected ticket APIs reject requests with invalid token (401)")
    void testInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected ticket APIs reject requests with expired token (401)")
    void testExpiredTokenReturns401() throws Exception {
        // Create service with -1000ms expiration to generate an expired token
        JwtService expiredJwtService = new JwtService("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", -1000);
        String expiredToken = expiredJwtService.generateToken("admin@example.com", "ADMIN");

        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Employee can create ticket and ticket is associated with current user")
    void testEmployeeCanCreateTicket() throws Exception {
        String token = obtainToken("Emp One", "emp_creator_" + UUID.randomUUID() + "@example.com", "Pass1234");

        CreateTicketRequest request = new CreateTicketRequest("Printer broken", "The office printer won't print.");

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.subject").value("Printer broken"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andReturn();

        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getUser()).isNotNull();
        assertThat(ticket.getSubject()).isEqualTo("Printer broken");
    }

    @Test
    @DisplayName("Employee can only view their own tickets")
    void testEmployeeViewsOnlyOwnTickets() throws Exception {
        String emailA = "emp_a_" + UUID.randomUUID() + "@example.com";
        String tokenA = obtainToken("User A", emailA, "Pass1234");

        String emailB = "emp_b_" + UUID.randomUUID() + "@example.com";
        String tokenB = obtainToken("User B", emailB, "Pass1234");

        // User A creates ticket A
        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTicketRequest("Ticket by A", "Details A"))))
                .andExpect(status().isCreated());

        // User B creates ticket B
        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTicketRequest("Ticket by B", "Details B"))))
                .andExpect(status().isCreated());

        // User A views tickets -> only Ticket by A
        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("Ticket by A"));
    }

    @Test
    @DisplayName("Employee cannot access another employee's ticket (403 Forbidden)")
    void testEmployeeCannotAccessAnotherEmployeesTicket() throws Exception {
        String tokenA = obtainToken("User A", "emp_owner_" + UUID.randomUUID() + "@example.com", "Pass1234");
        String tokenB = obtainToken("User B", "emp_attacker_" + UUID.randomUUID() + "@example.com", "Pass1234");

        // User A creates a ticket
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTicketRequest("Private ticket", "Confidential issue"))))
                .andExpect(status().isCreated())
                .andReturn();

        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // User A can view it (200 OK)
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Private ticket"));

        // User B attempts to access User A's ticket -> 403 Forbidden
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Admin can view all tickets and any individual ticket")
    void testAdminCanViewAllTickets() throws Exception {
        // Seeded admin
        String adminToken = loginAndGetToken("admin@example.com", "Admin123");

        // Create a ticket with an employee
        String employeeToken = obtainToken("Emp Client", "emp_client_" + UUID.randomUUID() + "@example.com", "Pass1234");
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTicketRequest("Issue for admin", "Help needed"))))
                .andExpect(status().isCreated())
                .andReturn();

        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // Admin can access employee's ticket
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId));

        // Admin can list all tickets
        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Agent can access tickets")
    void testAgentCanAccessTickets() throws Exception {
        String agentToken = loginAndGetToken("agent@example.com", "Agent123");

        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
    }

    private String obtainToken(String name, String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest(name, email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.token();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.token();
    }
}
