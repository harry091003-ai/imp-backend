package com.example.UserService.repository;
import com.example.UserService.entity.RoleName;
import com.example.UserService.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

     Boolean existsByUsername(String roleName);
     Boolean existsByEmail(String email);

    User findByEmail(String email);
    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.rolename = :role")
    List<User> findUsersByRole(@Param("role")RoleName role);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.id = :id AND r.rolename = :role")
    Optional<User> findEngineerWithID(@Param("id") UUID id ,@Param("role") RoleName role);

    //for getting all the Engineers under this Supervisor  using supervisor id from Db
    List<User> findBySupervisor_Userid(UUID supervisorId);




    //Query for You are combining 3 concerns into ONE DB query:
    //✅ 1. Search (filtering)
    //Search by:username or email
    //✅ 2. Role-based access control (RBAC)
    //Admin → sees everything
    //Supervisor → sees their engineers
    //Engineer → sees:themselves
    //their supervisor
    //teammates (same supervisor)
    //✅ 3. Pagination
    @Query("""
SELECT u FROM User u
WHERE
(LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
AND
(
  :isAdmin = true

  OR (:isSupervisor = true AND u.supervisor.userid = :authUserId)

  OR (:isEngineer = true AND
        (
            u.userid = :authUserId
             OR u.userid = (
                                    SELECT e.supervisor.userid
                                    FROM User e
                                    WHERE e.userid = :authUserId
                                )
            OR  (u.supervisor IS NOT NULL AND u.supervisor.userid = (
                     SELECT e.supervisor.userid FROM User e WHERE e.userid = :authUserId
                 ))
        )
     )
)
""")
    Page<User> searchUsers(
            @Param("query") String query,
            @Param("isAdmin") boolean isAdmin,
            @Param("isSupervisor") boolean isSupervisor,
            @Param("isEngineer") boolean isEngineer,
            @Param("authUserId") UUID authUserId,
            Pageable pageable
    );

    // AI plan phase 1: JOIN FETCH loads the lazy roles in this one query.
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.userid IN :userIds")
    List<User> findWithRolesByUseridIn(@Param("userIds") List<UUID> userIds);
}

