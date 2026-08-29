package com.example.AuthService.entity;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userid;

    @Column(nullable = false, unique = true, length = 25)
    private String username;

    @Email
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

//    @Enumerated(value = EnumType.STRING)
//    private Status status=Status.INACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

//X--
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private LocalDateTime lastLoginAt;

    @Column(nullable = true)
    private  LocalDateTime lastLogoutAt;

    @Column(nullable = true)
    private LocalDateTime passwordUpdatedAt;
//X--

    @Builder.Default
    @Column(nullable = false)
    private Integer failedLoginAttempts = 0;

    //for multiple failed login attempts.
    @Builder.Default
    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean accountLocked = false;

//    for initially when user register the account is not enabled.
    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

// this field is used to invalidate the JWT token when user change the password or logout
@Builder.Default
@Column(nullable = false)
private Integer TokenVersion=0;


//since we are using ManyToMany so there is no parent child relationship (Neither entity is a child of the other.)
//     ManyToMany creates a new intermediate table which store each User and there Role
//     If we want to give the specific name for our new table  and there columns then we have to  use @JoinTable.ie:user_roles that link both the tables through there primary key.
//    means the Columns of this NEw Table Makes The Foreignkey Connection with the primary key of our User and Role Table.
//    ie:user_roles.user_id → references User.userid
//    ie:user_roles.role_id → references Role.id

// we are doing unidirectional ManyToMany Mapping so from Role Entity we can not access User...But From User Entity we can access the Roles .we did this because need then this thing of getting user through roles Entity.

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    // if we are using @Builder Annotation at the top of a class then we also need to add
    // @Builder.Default annotation to that class each field that contains default value..otherside  when
    // we are  creating object using builder().build() then that object will take null value instead of
    // that default value
    @Builder.Default
    private Set<Role> roles=new HashSet<>();// we are using Set<Role> instead of List<Role> because User should not have duplicate roles


}