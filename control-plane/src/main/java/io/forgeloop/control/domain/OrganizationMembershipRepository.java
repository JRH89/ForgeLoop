package io.forgeloop.control.domain;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, String> { Optional<OrganizationMembership> findByOrganization_IdAndSubject(String organizationId, String subject); List<OrganizationMembership> findByOrganization_IdOrderBySubjectAsc(String organizationId); }
