package br.edu.unip.siab.terminal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {

    List<Terminal> findAllByOrderByIdAsc();
}
