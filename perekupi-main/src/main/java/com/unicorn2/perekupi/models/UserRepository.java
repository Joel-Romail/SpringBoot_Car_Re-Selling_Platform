package com.unicorn2.perekupi.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;


public interface UserRepository extends JpaRepository<User,Integer> {
    List<User> findByCompany(String company);
    List<User> findByNameAndPassword(String name, String password);

    @Modifying
    @Transactional
    @Query("DELETE FROM User u WHERE LOWER(u.name) = LOWER(:name)")
    int deleteByName(@Param("name") String name);  // returns rows deleted
}
