# Onboarding sans connexion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer le parcours avec connexion par un onboarding fondé sur le compte bancaire, avec identité issue du référentiel, préfixe `10005` imposé et finalisation en utilisateur.

**Architecture:** Le backend reconstruit le numéro de compte à partir du suffixe de 18 chiffres, puis utilise `BankAccount` comme référentiel d’identité. Une session opaque protège uniquement la progression de l’onboarding, sans constituer une authentification utilisateur. Le frontend Angular ne propose aucun login et conserve les données de session temporaire seulement pour la durée du parcours.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Flyway, PostgreSQL, Angular 21, Reactive Forms, Vitest.

## Global Constraints

- Le préfixe est exactement `10005` et le numéro complet contient exactement 23 chiffres.
- L’API de vérification reçoit exclusivement un suffixe de 18 chiffres ; elle ajoute elle-même le préfixe.
- `firstName` et `lastName` viennent exclusivement de `BankAccount` et ne sont jamais modifiables par le navigateur.
- L’e-mail est le seul contact saisi pendant l’onboarding ; `phoneNumber` est persisté à `null`.
- Une session inachevée représente un prospect ; un parcours finalisé crée un client au statut `USER`.
- Le login, JWT applicatif et ses écrans/routes/services sont supprimés. La réinitialisation de PIN reste publique.
- WhatsApp et le remplissage futur du téléphone sont hors périmètre.

---

## File structure

- `backend/src/main/resources/db/migration/V3__bank_identity_and_user_status.sql`: ajoute prénom/nom bancaires, convertit les données de développement au format à 23 chiffres et introduit `USER`.
- `backend/src/main/java/.../account/*`: contrat de vérification basé sur le suffixe et réponse contenant l’identité bancaire.
- `backend/src/main/java/.../onboarding/*`: création du client depuis l’identité bancaire, avec téléphone nul.
- `backend/src/main/java/.../common/security/SecureSessionTokenGenerator.java`: générateur de jetons opaques de session d’onboarding.
- `backend/src/main/java/.../auth/**`: supprimé, car il ne porte que le login/JWT applicatif.
- `frontend/src/app/core/services/onboarding-api.ts` et `onboarding-state.ts`: appels HTTP et état temporaire de session/identité.
- `frontend/src/app/features/onboarding/**`: écrans du parcours sans login ; `account-verification` et `kyc` deviennent fonctionnels.
- `frontend/src/app/features/auth/login/**`, `frontend/src/app/features/auth/auth.routes.ts`, `core/guards/auth-guard.ts`, `core/interceptors/jwt-interceptor.ts`, `core/services/auth.ts`, `core/services/token.ts`: supprimés. `features/auth/forgot-pin/**` est déplacé vers `features/pin-reset/request/**` et un écran `features/pin-reset/confirm/**` est créé.

### Task 1: Migrer le référentiel bancaire et le statut utilisateur

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__bank_identity_and_user_status.sql`
- Modify: `backend/src/main/java/com/firstagent/backend/account/entity/BankAccount.java`
- Modify: `backend/src/main/java/com/firstagent/backend/common/enums/CustomerStatus.java`
- Test: `backend/src/test/java/com/firstagent/backend/account/BankAccountMappingTest.java`

**Interfaces:**
- Produces: `BankAccount#getFirstName(): String`, `BankAccount#getLastName(): String`, `CustomerStatus.USER`.

- [ ] **Step 1: Write the failing entity-mapping test**

```java
@Test
void mapsSeparateBankIdentityFields() {
    BankAccount account = entityManager.find(BankAccount.class, seededAccountId);
    assertThat(account.getFirstName()).isEqualTo("Jean");
    assertThat(account.getLastName()).isEqualTo("Dupont");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -Dtest=BankAccountMappingTest test`

Expected: compilation failure because `getFirstName` and `getLastName` do not exist.

- [ ] **Step 3: Add the Flyway migration and entity fields**

```sql
ALTER TABLE bank_accounts ADD COLUMN first_name VARCHAR(100);
ALTER TABLE bank_accounts ADD COLUMN last_name VARCHAR(100);
UPDATE bank_accounts SET first_name = split_part(owner_full_name, ' ', 1),
                         last_name = substring(owner_full_name from position(' ' in owner_full_name) + 1);
UPDATE bank_accounts SET account_number = '10005123451234567890123'
    WHERE account_number = 'CM21100010000123456789012';
UPDATE bank_accounts SET account_number = '10005987659876543210987'
    WHERE account_number = 'CM21100010000987654321098';
ALTER TABLE bank_accounts ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE bank_accounts ALTER COLUMN last_name SET NOT NULL;
ALTER TABLE customers DROP CONSTRAINT customers_status_check;
ALTER TABLE customers ADD CONSTRAINT customers_status_check
    CHECK (status IN ('USER', 'BLOCKED', 'SUSPENDED'));
UPDATE customers SET status = 'USER' WHERE status = 'ACTIVE';
```

Map `first_name` and `last_name` in `BankAccount`, add `USER` to `CustomerStatus`, and update the default customer status to `USER`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -Dtest=BankAccountMappingTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: store bank identity and user status"
```

### Task 2: Vérifier un suffixe de compte et produire une session opaque

**Files:**
- Create: `backend/src/main/java/com/firstagent/backend/common/security/SecureSessionTokenGenerator.java`
- Modify: `backend/src/main/java/com/firstagent/backend/account/dto/AccountVerificationRequest.java`
- Modify: `backend/src/main/java/com/firstagent/backend/account/dto/AccountVerificationResponse.java`
- Modify: `backend/src/main/java/com/firstagent/backend/account/service/AccountServiceImpl.java`
- Modify: `backend/src/main/java/com/firstagent/backend/account/controller/AccountController.java`
- Test: `backend/src/test/java/com/firstagent/backend/account/AccountVerificationIntegrationTest.java`

**Interfaces:**
- Consumes: `BankAccount#getFirstName()` and `BankAccount#getLastName()`.
- Produces: `AccountVerificationRequest { String accountSuffix; }` and `AccountVerificationResponse { boolean eligible; String firstName; String lastName; String sessionToken; long expiresInSeconds; }`.

- [ ] **Step 1: Write failing API tests**

```java
mockMvc.perform(post("/api/v1/accounts/verify")
        .contentType(APPLICATION_JSON)
        .content("{\"accountSuffix\":\"123451234567890123\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.firstName").value("Jean"))
    .andExpect(jsonPath("$.data.lastName").value("Dupont"));

mockMvc.perform(post("/api/v1/accounts/verify")
        .contentType(APPLICATION_JSON)
        .content("{\"accountSuffix\":\"123\"}"))
    .andExpect(status().isBadRequest());
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -Dtest=AccountVerificationIntegrationTest test`

Expected: the valid request is rejected because `accountSuffix` is not yet part of the DTO.

- [ ] **Step 3: Implement suffix validation and opaque tokens**

```java
public static final String ACCOUNT_PREFIX = "10005";

@NotBlank
@Pattern(regexp = "\\d{18}", message = "Le numéro doit contenir 18 chiffres")
private String accountSuffix;

String accountNumber = ACCOUNT_PREFIX + request.getAccountSuffix();
```

Implement `SecureSessionTokenGenerator.generate()` with 32 random bytes and URL-safe Base64 without padding. Persist that token in `OnboardingSession`; remove the `TokenProvider` dependency from `AccountServiceImpl`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -Dtest=AccountVerificationIntegrationTest test`

Expected: PASS; a suffix yields the bank identity and an opaque session token.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: verify bank account suffixes"
```

### Task 3: Créer le profil depuis les données bancaires

**Files:**
- Modify: `backend/src/main/java/com/firstagent/backend/onboarding/dto/KycRequest.java`
- Modify: `backend/src/main/java/com/firstagent/backend/onboarding/service/OnboardingServiceImpl.java`
- Modify: `backend/src/main/java/com/firstagent/backend/onboarding/dto/OnboardingCompletionResponse.java`
- Modify: `backend/src/main/java/com/firstagent/backend/onboarding/controller/OnboardingController.java`
- Test: `backend/src/test/java/com/firstagent/backend/onboarding/OnboardingProfileIntegrationTest.java`

**Interfaces:**
- Consumes: an `X-Session-Token` from Task 2 and `KycRequest { String email; }`.
- Produces: a `Customer` whose names equal its linked `BankAccount` names, whose `phoneNumber` is `null`, and whose status is `USER` after `/complete`.

- [ ] **Step 1: Write failing profile tests**

```java
assertThat(customer.getFirstName()).isEqualTo("Jean");
assertThat(customer.getLastName()).isEqualTo("Dupont");
assertThat(customer.getPhoneNumber()).isNull();

mockMvc.perform(post("/api/v1/onboarding/complete")
        .header("X-Session-Token", sessionToken))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.userStatus").value("USER"));
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -Dtest=OnboardingProfileIntegrationTest test`

Expected: names are still taken from the KYC request and `userStatus` is absent.

- [ ] **Step 3: Implement the bank-owned profile fields**

```java
public class KycRequest {
    @Email(message = "Format d'email invalide")
    @Size(max = 150)
    private String email;
}

Customer customer = Customer.builder()
    .firstName(session.getBankAccount().getFirstName())
    .lastName(session.getBankAccount().getLastName())
    .email(kycRequest.getEmail())
    .phoneNumber(null)
    .status(CustomerStatus.USER)
    .build();
```

Return `userStatus` in the completion response and replace the login-oriented completion message with a message that confirms access preparation for WhatsApp.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -Dtest=OnboardingProfileIntegrationTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: create users from bank identity"
```

### Task 4: Retirer l’authentification tout en gardant la réinitialisation du PIN

**Files:**
- Delete: `backend/src/main/java/com/firstagent/backend/auth/controller/AuthController.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/service/AuthService.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/service/AuthServiceImpl.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/dto/LoginRequest.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/dto/LoginResponse.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/security/CustomUserDetails.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/security/CustomUserDetailsService.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/security/JwtAuthenticationFilter.java`
- Delete: `backend/src/main/java/com/firstagent/backend/auth/security/JwtProvider.java`
- Delete: `backend/src/main/java/com/firstagent/backend/common/security/TokenProvider.java`
- Delete: `backend/src/main/java/com/firstagent/backend/config/JwtConfig.java`
- Modify: `backend/src/main/java/com/firstagent/backend/config/SecurityConfig.java`
- Modify: `backend/pom.xml`
- Test: `backend/src/test/java/com/firstagent/backend/config/PublicApiSecurityTest.java`

**Interfaces:**
- Produces: no `/api/v1/auth/login` mapping; `/api/v1/pin/reset/request` and `/api/v1/pin/reset/confirm` remain public.

- [ ] **Step 1: Write failing security tests**

```java
mockMvc.perform(post("/api/v1/auth/login"))
    .andExpect(status().isNotFound());

mockMvc.perform(post("/api/v1/pin/reset/request")
        .contentType(APPLICATION_JSON)
        .content("{\"accountNumber\":\"10005123451234567890123\"}"))
    .andExpect(status().isNotEqualTo(HttpStatus.FORBIDDEN.value()));
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -Dtest=PublicApiSecurityTest test`

Expected: `/api/v1/auth/login` is still mapped.

- [ ] **Step 3: Delete login/JWT code and simplify security**

```java
http.csrf(AbstractHttpConfigurer::disable)
    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
```

Remove JJWT dependencies from `pom.xml`. Keep `PinController` and `PinResetServiceImpl` unchanged apart from accepting the full 23-digit account number in `PinResetRequest` validation.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -Dtest=PublicApiSecurityTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "refactor: remove login authentication"
```

### Task 5: Construire le parcours Angular sans connexion

**Files:**
- Create: `frontend/src/app/core/services/onboarding-api.ts`
- Modify: `frontend/src/app/core/services/onboarding-state.ts`
- Modify: `frontend/src/app/core/models/onboarding-session.model.ts`
- Modify: `frontend/src/app/features/onboarding/account-verification/account-verification.ts`
- Modify: `frontend/src/app/features/onboarding/account-verification/account-verification.html`
- Modify: `frontend/src/app/features/onboarding/kyc/kyc.ts`
- Modify: `frontend/src/app/features/onboarding/kyc/kyc.html`
- Modify: `frontend/src/app/features/onboarding/success/success.ts`
- Modify: `frontend/src/app/features/onboarding/success/success.html`
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.config.ts`
- Create: `frontend/src/app/features/pin-reset/request/**`
- Create: `frontend/src/app/features/pin-reset/confirm/**`
- Delete: `frontend/src/app/features/auth/**`
- Delete: `frontend/src/app/core/guards/auth-guard.ts`
- Delete: `frontend/src/app/core/interceptors/jwt-interceptor.ts`
- Delete: `frontend/src/app/core/services/auth.ts`
- Delete: `frontend/src/app/core/services/token.ts`
- Test: `frontend/src/app/features/onboarding/account-verification/account-verification.spec.ts`
- Test: `frontend/src/app/features/onboarding/kyc/kyc.spec.ts`

**Interfaces:**
- Consumes: `POST /api/v1/accounts/verify` with `{ accountSuffix: string }`.
- Produces: `OnboardingState` exposing `sessionToken`, `firstName`, `lastName`, and `email` for the active prospect.

- [ ] **Step 1: Write failing component tests**

```ts
it('keeps the 10005 prefix outside the editable control', () => {
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('[data-testid="account-prefix"]').textContent)
    .toContain('10005');
  expect(fixture.nativeElement.querySelector('input').value).toBe('');
});

it('renders bank names as text, not editable inputs', () => {
  state.setIdentity({ firstName: 'Jean', lastName: 'Dupont' });
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('input[name="firstName"]')).toBeNull();
  expect(fixture.nativeElement.textContent).toContain('Jean');
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- --runInBand`

Expected: the account and KYC components have no reactive forms or identity rendering.

- [ ] **Step 3: Implement API state, routes and views**

```ts
readonly accountSuffix = new FormControl('', {
  nonNullable: true,
  validators: [Validators.required, Validators.pattern(/^\d{18}$/)],
});

verifyAccount(): void {
  this.api.verifyAccount(this.accountSuffix.value).subscribe(({ data }) => {
    this.state.setSession(data.sessionToken);
    this.state.setIdentity({ firstName: data.firstName, lastName: data.lastName });
    this.router.navigate(['/onboarding/kyc']);
  });
}
```

Render the mask as `10005 _____ ___________ __`; keep `10005` in a non-input element. In KYC, bind only an e-mail form control and display `firstName`/`lastName` as labels. Move the existing forgot-PIN view to `features/pin-reset/request`, create `features/pin-reset/confirm` for `{ resetToken, newPin, newPinConfirmation }`, remove login navigation, and configure `HttpClient` in `app.config.ts` with `provideHttpClient()`.

- [ ] **Step 4: Run tests and build to verify they pass**

Run: `npm test -- --runInBand && npm run build`

Expected: all Angular tests pass and the production bundle is generated.

- [ ] **Step 5: Record the completed files**

List the modified frontend files in the delivery note. Git commits are intentionally skipped because the workspace has no usable Git repository.

### Task 6: Vérification de bout en bout et documentation API

**Files:**
- Modify: `backend/src/main/java/com/firstagent/backend/config/SwaggerConfig.java`
- Create: `backend/README.md`
- Test: `backend/src/test/java/com/firstagent/backend/OnboardingEndToEndTest.java`

**Interfaces:**
- Consumes: the endpoints produced by Tasks 2–4 and the Angular contract from Task 5.
- Produces: documented public onboarding and PIN-reset API with no login operation.

- [ ] **Step 1: Write the failing end-to-end API test**

```java
// verify suffix -> submit email/PIN -> upload three required documents
// -> accept terms -> complete
assertThat(completion.getData().getUserStatus()).isEqualTo("USER");
assertThat(customerRepository.findById(completion.getData().getCustomerId()).orElseThrow()
    .getPhoneNumber()).isNull();
```

- [ ] **Step 2: Run the test to verify it fails before the full flow is wired**

Run: `./mvnw -Dtest=OnboardingEndToEndTest test`

Expected: failure identifies the first endpoint whose new contract is incomplete.

- [ ] **Step 3: Align OpenAPI and project documentation**

Update the OpenAPI description to remove authentication/login wording, describe the fixed prefix and 18-digit suffix, and document that WhatsApp is out of scope. Document `X-Session-Token` as an onboarding-progress token rather than a bearer login token.

- [ ] **Step 4: Run complete verification**

Run: `./mvnw test && npm --prefix frontend test -- --runInBand && npm --prefix frontend run build`

Expected: all backend tests, frontend tests and the Angular production build succeed.

- [ ] **Step 5: Record the completed files**

List the modified backend and frontend files in the delivery note. Git commits are intentionally skipped because the workspace has no usable Git repository.
