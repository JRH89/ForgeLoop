package io.forgeloop.support.domain;
import jakarta.persistence.*;
@Entity public class Organization { @Id private String id; private String name; protected Organization() {} public Organization(String id,String name){this.id=id;this.name=name;} public String getId(){return id;} }
