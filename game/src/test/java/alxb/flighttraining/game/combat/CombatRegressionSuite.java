package alxb.flighttraining.game.combat;
public final class CombatRegressionSuite {
    public static void main(String[] args)throws Exception {
        DamageChecks.main(args);DamageVisualChecks.main(args);
        alxb.flighttraining.game.network.NetworkCombatChecks.main(args);
        alxb.flighttraining.game.ui.NetworkReplicaChecks.main(args);
        Class.forName("PhysicsCheck").getMethod("main",String[].class).invoke(null,(Object)args);
        Class.forName("StressCheck").getMethod("main",String[].class).invoke(null,(Object)args);
        System.out.println("PASS all combat regression checks");
    }
}
