package io.forgeloop.control.domain;
import jakarta.persistence.*;
@Entity public class VerificationGate { @Id @GeneratedValue(strategy=GenerationType.UUID) private String id; @ManyToOne(optional=false) private FeatureRun run; private String name; private boolean required=true; private String state="PENDING"; protected VerificationGate(){} VerificationGate(FeatureRun run,String name){this.run=run;this.name=name;} public String getId(){return id;} public String getName(){return name;} public boolean isRequired(){return required;} public String getState(){return state;} }
