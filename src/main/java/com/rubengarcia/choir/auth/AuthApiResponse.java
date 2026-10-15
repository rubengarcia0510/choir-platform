package com.rubengarcia.choir.auth;

public sealed interface AuthApiResponse
        permits AuthController.AuthResponse,
                AuthController.MessageResponse,
                AuthController.ErrorResponse {
}
