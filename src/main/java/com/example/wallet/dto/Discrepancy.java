package com.example.wallet.dto;

public record Discrepancy(String kind, String ref, long expectedPaise, long actualPaise) {}

