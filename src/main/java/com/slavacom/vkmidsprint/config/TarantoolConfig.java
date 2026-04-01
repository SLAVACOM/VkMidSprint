package com.slavacom.vkmidsprint.config;

import com.slavacom.vkmidsprint.property.TarantoolProperty;
import io.tarantool.client.TarantoolClient;
import io.tarantool.client.crud.TarantoolCrudClient;
import io.tarantool.client.factory.TarantoolFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.tarantool.client.factory.TarantoolFactory.*;

@Configuration
@RequiredArgsConstructor
public class TarantoolConfig {

	private final TarantoolProperty property;

	@Bean
	public TarantoolCrudClient crudClient() throws Exception {
		return crud()
				.withHost(property.getHost())
				.withPort(property.getPort())
				.withUser(property.getUser())
				.withPassword(property.getPassword())
				.build();
	}

	@Bean
	public TarantoolClient boxClient() throws Exception {
		return TarantoolFactory.box()
				.withHost(property.getHost())
				.withPort(property.getPort())
				.withUser(property.getUser())
				.withPassword(property.getPassword())
				.build();
	}
}
