/*
 * Copyright 2025 EPAM Systems.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.digital.data.platform.keycloak.rest.api.ext;

import static org.keycloak.models.jpa.PaginationUtils.paginateQuery;
import static org.keycloak.utils.StreamsUtil.closing;

import com.epam.digital.data.platform.keycloak.rest.api.ext.dto.SearchUsersByRoleAndAttributesRequestDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaUserProvider;
import org.keycloak.models.jpa.UserAdapter;
import org.keycloak.models.jpa.entities.UserAttributeEntity;
import org.keycloak.models.jpa.entities.UserEntity;
import org.keycloak.models.jpa.entities.UserRoleMappingEntity;

public class ExtendedJpaUserProvider extends JpaUserProvider {

  private final KeycloakSession session;

  public ExtendedJpaUserProvider(KeycloakSession session, EntityManager em) {
    super(session, em);
    this.session = session;
  }

  public Stream<UserModel> searchForUserStream(RealmModel realm,
      SearchUsersByRoleAndAttributesRequestDto searchDto) {
    CriteriaBuilder builder = em.getCriteriaBuilder();
    CriteriaQuery<UserEntity> query = builder.createQuery(UserEntity.class);
    Root<UserRoleMappingEntity> roleRoot = query.from(UserRoleMappingEntity.class);
    Join<UserRoleMappingEntity, UserEntity> userJoin = roleRoot.join("user");

    var predicates = buildPredicates(realm, searchDto, builder, roleRoot, userJoin);

    query.select(userJoin).distinct(true)
        .where(predicates)
        .orderBy(builder.asc(userJoin.get(UserModel.USERNAME)));

    TypedQuery<UserEntity> paginateQuery =
        paginateQuery(em.createQuery(query),
            searchDto.getPagination().getOffset(),
            searchDto.getPagination().getLimit());

    return closing(paginateQuery.getResultStream())
        .map(user -> new UserAdapter(session, realm, em, user));
  }

  private Predicate[] buildPredicates(RealmModel realm,
      SearchUsersByRoleAndAttributesRequestDto searchDto, CriteriaBuilder builder,
      Root<UserRoleMappingEntity> roleRoot, Join<UserRoleMappingEntity, UserEntity> userJoin) {
    List<Predicate> predicates = new ArrayList<>();

    predicates.add(builder.equal(userJoin.get("realmId"), realm.getId()));

    if (searchDto.getEnabled() != null) {
      predicates.add(builder.equal(userJoin.get("enabled"), searchDto.getEnabled()));
    }
    if (searchDto.getUsername() != null) {
      predicates.add(builder.equal(userJoin.get("username"), searchDto.getUsername()));
    }
    if (searchDto.getRoleName() != null) {
      RoleModel role = realm.getRole(searchDto.getRoleName());
      predicates.add(builder.equal(roleRoot.get("roleId"),
          role != null ? role.getId() : searchDto.getRoleName()));
    }

    predicates.addAll(
        createPredicatesEquals(searchDto.getAttributesEquals(), builder, userJoin));
    predicates.addAll(
        createPredicatesStartsWith(searchDto.getAttributesStartsWith(), builder,
            userJoin));
    predicates.addAll(
        createPredicatesInList(searchDto.getAttributesThatAreStartFor(), builder,
            userJoin));

    return predicates.toArray(new Predicate[0]);
  }

  private List<Predicate> createPredicatesEquals(
      Map<String, List<String>> attributeMap,
      CriteriaBuilder builder, Join<UserRoleMappingEntity, UserEntity> userJoin) {
    return attributeMap.entrySet().stream()
        .map(entry -> {
          Join<UserEntity, UserAttributeEntity> attributesJoin = userJoin.join("attributes", JoinType.LEFT);
          Predicate keyPredicate = builder.equal(attributesJoin.get("name"), entry.getKey());
          Predicate valuePredicate = attributesJoin.get("value").in(entry.getValue());
          return builder.and(keyPredicate, valuePredicate);
        })
        .collect(Collectors.toList());
  }

  private List<Predicate> createPredicatesStartsWith(
      Map<String, List<String>> attributeMap,
      CriteriaBuilder builder, Join<UserRoleMappingEntity, UserEntity> userJoin) {
    return attributeMap.entrySet().stream()
        .map(entry -> {
          Join<UserEntity, UserAttributeEntity> attributesJoin = userJoin.join("attributes", JoinType.LEFT);
          Predicate keyPredicate = builder.equal(attributesJoin.get("name"), entry.getKey());
          Predicate valuePredicate = builder.or(entry.getValue().stream()
              .map(value -> builder.like(attributesJoin.get("value"),
                  value + "%"))
              .toArray(Predicate[]::new));
          return builder.and(keyPredicate, valuePredicate);
        })
        .collect(Collectors.toList());
  }

  private List<Predicate> createPredicatesInList(
      Map<String, List<String>> attributeMap,
      CriteriaBuilder builder, Join<UserRoleMappingEntity, UserEntity> userJoin) {
    return attributeMap.entrySet().stream()
        .map(entry -> {
          Join<UserEntity, UserAttributeEntity> attributesJoin = userJoin.join("attributes", JoinType.LEFT);
          Predicate keyPredicate = builder.equal(attributesJoin.get("name"), entry.getKey());
          Predicate valuePredicate = attributesJoin.get("value").in(convertToStartFor(entry.getValue()));
          return builder.and(keyPredicate, valuePredicate);
        })
        .collect(Collectors.toList());
  }

  private static List<String> convertToStartFor(List<String> input) {
    return input == null ? Collections.emptyList() : input.stream()
        .map(ExtendedJpaUserProvider::generatePrefixes)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  private static List<String> generatePrefixes(String input) {
    return IntStream.rangeClosed(1, input.length())
        .mapToObj(i -> input.substring(0, i))
        .collect(Collectors.toList());
  }

}