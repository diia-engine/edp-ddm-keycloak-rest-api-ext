/*
 * Copyright 2025 EPAM
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.digital.data.platform.keycloak.rest.api.ext;

import static org.assertj.core.api.Assertions.assertThat;

import javax.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mockito;

@DisplayName("ExtendedJpaUserProviderFactory Test")
class ExtendedJpaUserProviderFactoryTest {

  ExtendedJpaUserProviderFactory factory = new ExtendedJpaUserProviderFactory();

  @Test
  @DisplayName("Should create ExtendedJpaUserProvider instance")
  void testCreate() {
    var session = Mockito.mock(KeycloakSession.class);
    var provider = Mockito.mock(JpaConnectionProvider.class);
    var entityManager = Mockito.mock(EntityManager.class);

    Mockito.when(session.getProvider(JpaConnectionProvider.class)).thenReturn(provider);
    Mockito.when(provider.getEntityManager()).thenReturn(entityManager);

    var result = factory.create(session);

    assertThat(result)
        .isNotNull()
        .isInstanceOf(ExtendedJpaUserProvider.class)
        .hasFieldOrPropertyWithValue("session", session);
  }
}
