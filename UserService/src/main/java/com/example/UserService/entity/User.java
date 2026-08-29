package com.example.UserService.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;

import java.time.LocalDateTime;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder// by using we can apply the builder().build() to make an object of this Entity class
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID userid;

    @Column(nullable = false, unique = true, length = 25)
    private String username;

    @Email
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Builder.Default
    @Enumerated(value = EnumType.STRING)
    private Status status=Status.INACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private LocalDateTime lastLoginAt;

    @Column(nullable = true)
    private LocalDateTime lastLogoutAt;

    @Column(nullable = true)
    private LocalDateTime passwordUpdatedAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer failedLoginAttempts = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean accountLocked = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    //since we are using ManyToMany so there is no parent child relationship (Neither entity is a child of the other.)

//     ManyToMany creates a new intermediate table which store each User and there Role
//     If we want to give the specific name for our new table  and there columns then we have to  use @JoinTable.ie:user_roles that link both the tables through there primary key.
//    means the Columns of this NEw Table MAkes The Foreignkey Connection with the primarykey of our User and Role Table.
//    ie:user_roles.user_id → references User.userid
//    ie:user_roles.role_id → references Role.id

// we are doing unidirectional ManyToMany Mapping so from Role Entity we can not access User...But From User Entity we can access the Roles .we did this because need then this thing of getting user through roles Entity.

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    // if we using @Builder Annotation at the top of a class then we also need to add
    // @Builder.Default annotation to that class each field that contains default value..otherside  when
    // we are  creating object using builder().build() then that object will take null value instead of
    // that default value
    @Builder.Default
    private Set<Role> roles=new HashSet<>();// we are using Set<Role> instead of List<Role> because User should not have duplicate roles


    // for assigning engineers to a supervisor we are using we are Applying  Self Referencing Relationship means both  @Manytoone and @oneMany in same entity
    // Supervisor reference (Many engineers → One supervisor)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private User supervisor;


    // Reverse mapping (One supervisor → Many engineers)
    @Builder.Default
    @JsonIgnore //since we have bidirectional relationship so used to avoid the infinite loop
    @OneToMany(mappedBy = "supervisor", fetch = FetchType.LAZY) // also navigating/linking that Set<User> engineers to  foreign key made by @Manytoone  using the mappedby
    private Set<User> engineers = new HashSet<>();
}