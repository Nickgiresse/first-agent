package com.firstagent.backend.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Origines autorisées à appeler l'API.
 *
 * <h2>Pourquoi la liste est configurable</h2>
 *
 * <p>Elle ne contenait que les adresses de développement, en dur. Chaque nouveau sous-domaine
 * obligeait donc à recompiler, et l'oubli ne se voit pas en test : un appel sans en-tête {@code
 * Origin}, tel que le fait {@code curl}, ne déclenche aucun contrôle et réussit. Le défaut
 * n'apparaît que dans un navigateur, sous la forme d'un « Invalid CORS request » que le parcours
 * affiche comme une erreur d'analyse JSON, ce qui n'oriente vers rien.
 *
 * <p>C'est exactement ce qui s'est produit le 10/08/2026 à la mise en ligne de {@code
 * onboarding-v2} : les contrôles en ligne de commande passaient, la saisie du numéro de compte
 * échouait.
 *
 * <h2>Ce qui n'est pas fait, et pourquoi</h2>
 *
 * <p>Pas de {@code allowedOriginPatterns("*")}. Avec des identifiants de session portés par un
 * en-tête, ouvrir à toute origine reviendrait à laisser n'importe quel site appeler l'API au nom du
 * client. La liste reste explicite, quitte à devoir y ajouter un sous-domaine.
 */
@Configuration
public class CorsConfiguration implements WebMvcConfigurer {

  /**
   * Origines admises, séparées par des virgules.
   *
   * <p>Le repli couvre le développement local. En déploiement, la variable {@code
   * APP_CORS_ALLOWED_ORIGINS} porte la liste réelle.
   */
  @Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}")
  private List<String> origines;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(origines.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "X-Session-Token")
        .maxAge(3600);
  }
}
