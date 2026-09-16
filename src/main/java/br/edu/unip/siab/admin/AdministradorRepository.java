package br.edu.unip.siab.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {

    Optional<Administrador> findByUsername(String username);

    boolean existsByUsername(String username);
}
