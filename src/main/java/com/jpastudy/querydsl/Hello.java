package com.jpastudy.querydsl;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Data
@Entity
public class Hello {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private int age;
}
