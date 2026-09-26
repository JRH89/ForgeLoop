package io.forgeloop.control.domain;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.EntityGraph;
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, String> { Optional<OrganizationMembership> findByOrganization_IdAndSubject(String organizationId, String subject); List<OrganizationMembership> findByOrganization_IdOrderBySubjectAsc(String organizationId); @EntityGraph(attributePaths="organization") List<OrganizationMembership> findBySubjectOrderByIdAsc(String subject); long countByOrganization_Id(String organizationId); }
