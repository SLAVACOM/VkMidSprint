package com.slavacom.vkmidsprint.property;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("tarantool")
public class TarantoolProperty {
	private String host;
	private int port;
	private String password;
	private String user;
}