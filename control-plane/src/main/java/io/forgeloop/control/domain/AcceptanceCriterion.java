package io.forgeloop.control.domain;
import jakarta.persistence.*;
@Entity public class AcceptanceCriterion { @Id @GeneratedValue(strategy=GenerationType.UUID) private String id; @ManyToOne(optional=false) private FeatureRun run; @Column(length=4000) private String statement; private String coverageState="PENDING"; protected AcceptanceCriterion(){} AcceptanceCriterion(FeatureRun run,String statement){this.run=run;this.statement=statement;} public String getId(){return id;} public String getStatement(){return statement;} public String getCoverageState(){return coverageState;} }
