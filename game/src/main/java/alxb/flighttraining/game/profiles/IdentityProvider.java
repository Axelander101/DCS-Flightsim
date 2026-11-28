package alxb.flighttraining.game.profiles;

/** Identity source boundary. Local identities are not proof of an authenticated online account. */
public interface IdentityProvider {
    record Identity(String provider,String subject,String displayName){}
    Identity currentIdentity();
}
