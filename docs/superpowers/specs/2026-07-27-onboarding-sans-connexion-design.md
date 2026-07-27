# Onboarding sans connexion

## Objectif

Permettre à un prospect de terminer son onboarding bancaire sans écran de connexion. Une fois l'onboarding terminé, la personne devient un utilisateur autorisé pour une future expérience WhatsApp. L'intégration du chat WhatsApp ne fait pas partie de ce périmètre.

## Parcours utilisateur

1. Le prospect voit un numéro de compte composé du préfixe fixe et non modifiable `10005`, suivi de trois zones de saisie visuelles : `_____ ___________ __`.
2. Il saisit les 18 chiffres restants. Le frontend et le backend reconstruisent le numéro complet de 23 chiffres avant la vérification.
3. Le backend consulte le référentiel bancaire. Si le compte est éligible, il retourne le prénom et le nom séparés.
4. Le frontend affiche ces deux données sous forme de libellés en lecture seule.
5. Le prospect renseigne uniquement son e-mail, crée son PIN, ajoute les documents requis et accepte les conditions.
6. Le backend crée le client avec le téléphone à `null`, termine la session et attribue le statut `USER`.

## Données et API

- `BankAccount` contient `firstName` et `lastName` séparés, fournis par le référentiel bancaire.
- La vérification de compte accepte uniquement le suffixe de 18 chiffres. Le backend ajoute lui-même le préfixe `10005`, ce qui évite qu'un client puisse le modifier.
- Les DTO KYC et de création de profil ne contiennent plus prénom, nom ni téléphone. Ils ne contiennent que l'e-mail et les données nécessaires à la création du PIN.
- `Customer.phoneNumber` reste nullable et est enregistré à `null` tant que WhatsApp n'est pas intégré.
- Le statut d'un client finalisé est `USER`. Une session d'onboarding non finalisée représente un prospect et ne crée pas de client actif.

## Éléments supprimés et conservés

- Suppression du login, des JWT applicatifs, des écrans et gardes Angular liés à la connexion, ainsi que des routes et services backend correspondants.
- Conservation de la réinitialisation du PIN comme API publique, basée sur le numéro de compte complet.
- Aucune fonctionnalité WhatsApp, aucun envoi de message et aucune redirection vers un chat dans cette livraison.

## Validation et erreurs

- Un suffixe qui ne contient pas exactement 18 chiffres est rejeté avec une erreur de validation.
- Un numéro reconstitué qui ne commence pas par `10005` est rejeté avec une erreur de validation.
- Compte introuvable : 404 ; compte non éligible : 403 ; compte déjà onboardé : erreur métier explicite.
- Les champs retournés par le référentiel bancaire ne sont jamais modifiables depuis le navigateur.

## Tests d'acceptation

- La vérification d'un suffixe valide renvoie le nom et le prénom du compte correspondant.
- Les suffixes trop courts, trop longs ou non numériques sont refusés.
- La création d'un client utilise les noms du compte bancaire et persiste un téléphone nul.
- Une fin d'onboarding attribue le statut `USER`.
- Les routes de login sont absentes ; la réinitialisation du PIN reste accessible.
- L'interface affiche le préfixe `10005` sans possibilité de le modifier et ne propose ni saisie de prénom, nom ou téléphone, ni écran de connexion.
