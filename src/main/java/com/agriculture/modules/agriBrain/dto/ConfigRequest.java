package com.agriculture.modules.agriBrain.dto;

import lombok.Data;

@Data
public class ConfigRequest {

    private String provider;
    private String model;
    private String apiKey;
}
