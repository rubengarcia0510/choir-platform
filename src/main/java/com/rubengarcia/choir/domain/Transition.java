package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("transitions")
public class Transition {
    @Id
    private Long id;

    @Column("choir_id")
    private Long choirId;

    private String name;
    private String description;

    protected Transition() {}

    public Long getId() { return id; }
    public Long getChoirId() { return choirId; }
    public void setChoirId(Long choirId) { this.choirId = choirId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
