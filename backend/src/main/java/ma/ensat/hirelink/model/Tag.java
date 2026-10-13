package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/** Tag libre attaché aux offres — relation @ManyToMany (Séance 3). */
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<JobOffer> offers = new HashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<JobOffer> getOffers() { return offers; }
    public void setOffers(Set<JobOffer> offers) { this.offers = offers; }
}
