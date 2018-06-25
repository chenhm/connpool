package com.chenhm.connpool;

import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.Data;

@Entity
@Data
public class Test {
    @Id
    String test;
}
