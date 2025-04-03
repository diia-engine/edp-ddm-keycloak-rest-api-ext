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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.epam.digital.data.platform.keycloak.rest.api.ext.dto.SearchUsersByRoleAndAttributesRequestDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaUserProvider;
import org.keycloak.models.jpa.entities.UserAttributeEntity;
import org.keycloak.models.jpa.entities.UserEntity;
import org.keycloak.models.jpa.entities.UserRoleMappingEntity;

@DisplayName("ExtendedJpaUserProvider Integration Test")
class ExtendedJpaUserProviderIT {

  private EntityManagerFactory entityManagerFactory;
  private EntityManager entityManager;
  private KeycloakSession session;
  private JpaUserProvider userProvider;
  private ExtendedJpaUserProvider extendedJpaUserProvider;

  private final String DEFAULT_ROLE = "officer";

  @BeforeEach
  void setUp() {
    entityManagerFactory = Persistence.createEntityManagerFactory("test-pu");
    entityManager = entityManagerFactory.createEntityManager();
    session = mock(KeycloakSession.class);
    userProvider = new JpaUserProvider(session, entityManager);
    extendedJpaUserProvider = new ExtendedJpaUserProvider(session, entityManager);

    when(session.users()).thenReturn(userProvider);

    entityManager.getTransaction().begin();
  }

  @AfterEach
  void tearDown() {
    if (entityManager.getTransaction().isActive()) {
      entityManager.getTransaction().commit();
    }
    entityManager.clear();
  }

  @Test
  @DisplayName("Should return user by username")
  void shouldReturnUserByUsername() {
    RealmModel realm = createRealm("test-realm");
    UserEntity user1 = createUser(realm, "user1", true, Collections.emptyMap());
    UserEntity user2 = createUser(realm, "user2", true, Collections.emptyMap());

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setEnabled(true);
    searchDto.setUsername("user1");

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUsername()).isEqualTo("user1");
  }

  @Test
  @DisplayName("Should return user by role")
  void shouldReturnUserByRole() {
    RoleModel role = mock(RoleModel.class);

    RealmModel realm = createRealm("test-realm");
    UserEntity user1 = createUser(realm, "user1", true, Collections.emptyMap());
    UserEntity user2 = createUser(realm, "user2", true, Collections.emptyMap());
    var user2Role = "op-regression";
    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, user2Role);

    when(realm.getRole(user2Role)).thenReturn(role);
    when(role.getId()).thenReturn(user2Role);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setEnabled(true);
    searchDto.setRoleName(user2Role);

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUsername()).isEqualTo("user2");
  }

  @Test
  @DisplayName("Should return an empty result when searching by a non-existent role")
  void shouldReturnEmptyWhenSearchingUserByNonExistentRole() {
    RoleModel role = mock(RoleModel.class);

    RealmModel realm = createRealm("test-realm");
    UserEntity user1 = createUser(realm, "user1", true, Collections.emptyMap());
    UserEntity user2 = createUser(realm, "user2", true, Collections.emptyMap());
    var user2Role = "op-regression";
    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, user2Role);

    when(realm.getRole(user2Role)).thenReturn(role);
    when(role.getId()).thenReturn(user2Role);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setEnabled(true);
    searchDto.setRoleName("hello_role");

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(0);
  }

  @Test
  @DisplayName("Should return only the enabled users")
  void shouldReturnOnlyEnabledUsers() {
    RealmModel realm = createRealm("test-realm");
    UserEntity user1 = createUser(realm, "user1", true, Collections.emptyMap());
    UserEntity user2 = createUser(realm, "user2", true, Collections.emptyMap());
    UserEntity user3 = createUser(realm, "user3", false, Collections.emptyMap());

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);
    addRoleMapping(user3, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setEnabled(true);

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(2);
    result.forEach(ent -> assertThat(ent.isEnabled()).isTrue());
  }

  @Test
  @DisplayName("Should return user with DRFO equal to the given value")
  void shouldReturnUserAttributeDrfoEqual() {
    RealmModel realm = createRealm("test-realm");
    var firstUserDrfo = "888999000";
    UserEntity user1 = createUser(realm, "user1", true, Map.of("drfo", List.of(firstUserDrfo)));
    UserEntity user2 = createUser(realm, "user2", true, Map.of("drfo", List.of("888999001")));
    UserEntity user3 = createUser(realm, "user3", false, Map.of("drfo", List.of("888999002")));

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);
    addRoleMapping(user3, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setAttributesEquals(Map.of("drfo", List.of(firstUserDrfo)));

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUsername()).isEqualTo("user1");
  }

  @Test
  @DisplayName("Should return users with DRFO matching any of the given values using OR condition")
  void shouldFindUsersAttributeDrfoEqualWithOrCondition() {
    RealmModel realm = createRealm("test-realm");
    var firstUserDRFO = "888999000";
    var secondUserDRFO = "888999001";
    UserEntity user1 = createUser(realm, "user1", true, Map.of("drfo", List.of(firstUserDRFO)));
    UserEntity user2 = createUser(realm, "user2", true, Map.of("drfo", List.of(secondUserDRFO)));
    UserEntity user3 = createUser(realm, "user3", false, Map.of("drfo", List.of("888999002")));

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);
    addRoleMapping(user3, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setAttributesEquals(Map.of("drfo", List.of(firstUserDRFO, secondUserDRFO)));

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getUsername()).isEqualTo("user1");
    assertThat(result.get(1).getUsername()).isEqualTo("user2");
  }

  @Test
  @DisplayName("Should return users with KATOTTG starting with the given value")
  void shouldFindUsersAttributeKatottgStartWithCondition() {
    RealmModel realm = createRealm("test-realm");

    UserEntity user1 = createUser(realm, "user1", true, Map.of("katottg", List.of("UA1013445123")));
    UserEntity user2 = createUser(realm, "user2", true, Map.of("katottg", List.of("UA1023445123")));
    UserEntity user3 = createUser(realm, "user3", false, Map.of("katottg", List.of("UA1033445123")));

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);
    addRoleMapping(user3, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setAttributesStartsWith(Map.of("katottg", List.of("UA101")));

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUsername()).isEqualTo("user1");
  }

  @Test
  @DisplayName("Should return users with KATOTTG starting with any given value using OR condition")
  void shouldFindUsersAttributeKatottgStartWithOrCondition() {
    RealmModel realm = createRealm("test-realm");

    UserEntity user1 = createUser(realm, "user1", true, Map.of("katottg", List.of("UA1013445123")));
    UserEntity user2 = createUser(realm, "user2", true, Map.of("katottg", List.of("UA1023445123")));
    UserEntity user3 = createUser(realm, "user3", false, Map.of("katottg", List.of("UA1033445123")));

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);
    addRoleMapping(user3, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setAttributesStartsWith(Map.of("katottg", List.of("UA101", "UA102")));

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getUsername()).isEqualTo("user1");
    assertThat(result.get(1).getUsername()).isEqualTo("user2");
  }

  @Test
  @DisplayName("Should return users with codes starting with the given value")
  void shouldFindUsersAttributeCodeThatAreStartFor() {
    RealmModel realm = createRealm("test-realm");

    UserEntity user1 = createUser(realm, "user1", true, Map.of("code", List.of("11111111")));
    UserEntity user2 = createUser(realm, "user2", true, Map.of("code", List.of("1111")));
    UserEntity user3 = createUser(realm, "user3", false, Map.of("code", List.of("11")));
    UserEntity user4 = createUser(realm, "user4", false, Map.of("code", List.of("1")));
    UserEntity user5 = createUser(realm, "user5", false, Map.of("code", List.of("2")));

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);
    addRoleMapping(user3, DEFAULT_ROLE);
    addRoleMapping(user4, DEFAULT_ROLE);
    addRoleMapping(user5, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setAttributesThatAreStartFor(Map.of("code", List.of("11111111")));

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(4);
    assertThat(result.get(0).getUsername()).isEqualTo("user1");
    assertThat(result.get(3).getUsername()).isEqualTo("user4");
  }

  @Test
  @DisplayName("Should return users with codes starting for any given value using OR condition")
  void shouldFindUsersAttributeCodeThatAreStartForOr() {
    RealmModel realm = createRealm("test-realm");

    UserEntity user1 = createUser(realm, "user1", true, Map.of("code", List.of("11111111")));
    UserEntity user2 = createUser(realm, "user2", true, Map.of("code", List.of("1111")));
    UserEntity user3 = createUser(realm, "user3", false, Map.of("code", List.of("22")));
    UserEntity user4 = createUser(realm, "user4", false, Map.of("code", List.of("2")));
    UserEntity user5 = createUser(realm, "user5", false, Map.of("code", List.of("3")));

    addRoleMapping(user1, DEFAULT_ROLE);
    addRoleMapping(user2, DEFAULT_ROLE);
    addRoleMapping(user3, DEFAULT_ROLE);
    addRoleMapping(user4, DEFAULT_ROLE);
    addRoleMapping(user5, DEFAULT_ROLE);

    SearchUsersByRoleAndAttributesRequestDto searchDto = new SearchUsersByRoleAndAttributesRequestDto();
    searchDto.setAttributesThatAreStartFor(Map.of("code", List.of("11111111", "22222222")));

    List<UserModel> result = extendedJpaUserProvider.searchForUserStream(realm, searchDto)
        .collect(Collectors.toList());

    assertThat(result).hasSize(4);
    assertThat(result.get(0).getUsername()).isEqualTo("user1");
    assertThat(result.get(3).getUsername()).isEqualTo("user4");
  }

  private RealmModel createRealm(String realmId) {
    RealmModel realm = mock(RealmModel.class);
    when(realm.getId()).thenReturn(realmId);
    return realm;
  }

  private UserEntity createUser(RealmModel realm, String username, boolean enabled,
      Map<String, List<String>> attributes) {
    UserEntity userEntity = new UserEntity();
    userEntity.setId(username);
    userEntity.setUsername(username);
    userEntity.setEnabled(enabled);
    userEntity.setRealmId(realm.getId());

    entityManager.persist(userEntity);

    attributes.forEach((key, values) -> {
      values.forEach(value -> {
        UserAttributeEntity attributeEntity = new UserAttributeEntity();
        attributeEntity.setId(UUID.randomUUID().toString());
        attributeEntity.setUser(userEntity);
        attributeEntity.setName(key);
        attributeEntity.setValue(value);
        entityManager.persist(attributeEntity);
      });
    });
    return userEntity;
  }

  private void addRoleMapping(UserEntity user, String roleId) {
    UserRoleMappingEntity roleMapping = new UserRoleMappingEntity();
    roleMapping.setUser(user);
    roleMapping.setRoleId(roleId);
    entityManager.persist(roleMapping);
  }
}