package com.firstagent.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée du service.
 *
 * <p>L'ordonnancement est activé pour le balayage des alertes comportementales : le journal scellé
 * prouve ce qui s'est passé, mais personne ne le relit en continu, et détecter pendant qu'il est
 * encore temps suppose que quelque chose le parcoure sans qu'on le lui demande.
 */
@EnableScheduling
@SpringBootApplication
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
