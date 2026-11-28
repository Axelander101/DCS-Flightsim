DSC – Hornet damage and multiplayer combat, 28 November 2026

This update is installed directly in the Java DSC project. No Supabase SQL update is needed.
All multiplayer participants must use this update (network protocol version 2).

USE
Create Lobby -> MODE: COMBAT, then ready/start. MODE: FORMATION keeps weapons safe.
Combat starts airborne and armed. In solo free flight select MASTER ARM before firing.
Training's unlimited ammunition setting is ignored in multiplayer combat.
Default keys (remappable in Settings -> Controls; an existing conflicting binding is preserved):
  U       MASTER ARM SAFE/ARM
  J       Radar OFF/ON
  T       Next radar contact
  Y       Toggle radar STT lock (0.7 second acquisition)
  SPACE   Cannon, 500 rounds in multiplayer
  M       IR missile release, 4 stores in multiplayer, 0.75 second release interval
  F3      Flare pair, 30 individual flares / 15 pairs
  F2      Damage / utilities page on lower display
  F5/F6   Left/right engine FIRE: isolate engine and select extinguishing circuit
  F7      Discharge single shared fire bottle; no repair or replenishment in flight
  F11/F12 Left/right engine selector
  I       Both engines
On Macs the function keys may require Fn. Check the Controls page for your actual bindings.
A radar lock is not required for passive IR acquisition. Point the nose at a hot target
inside the IR cone and wait for seeker lock, or fire an unguided missile. Flare decoys
compete with engine heat. Radar OFF does not disable the passive IR seeker or RWR.
RWR reports radar search/STT, not an invented radar warning for a passive IR launch.

DAMAGE
Cannon and missile impacts affect components near the impact, including wings, stabilators,
fins, separate engines, generators, hydraulic pumps, fuel, air data, INS, radar, FCS and SMS.
Lost wings/tails change lift, drag, roll/pitch/yaw authority. Engine asymmetry causes yaw.
A remaining generator/hydraulic circuit can keep systems working; losing both causes
power/control loss. Fuel leaks and engine fires persist. The FIRE button isolates an
engine; DISCH extinguishes with the available bottle, without repairing hardware.
Structural triangle regions detach from each aircraft's own mesh; other aircraft and
cached model assets remain intact. Engine smoke/fire and cockpit cautions reflect damage.
A new flight resets damage and stores. Damage is not permanent account progression.

SCOPE AND LIMITS
The current playable, AI and network aircraft are F/A-18C Hornets. There are no ten
independently implemented aircraft types in the current code. These are Hornet-oriented
game systems, not a complete or certified 1:1 reproduction of the original aircraft.
APG-73-style RWS/STT, passive IR, gun lead, RWR and utilities have functional behavior,
but full radar modes, RF-guided missiles, chaff/RF decoys, complete original cockpit
switchology, navigation/weapon computers and detailed hydraulic circuit routing remain
outside this update. Ammunition blast energy, geometry and flight reversion are game
approximations. Not every original panel switch has a new function.

The host owns weapon creation, cooldowns, ammunition, target envelope/dwell, projectile
sweeps, component damage and combat snapshots. A client cannot submit an opponent hit
or health value. Each aircraft still flies on its own client; this is not full server-side
flight authority or competitive anti-cheat. A client can concede only its own fatal crash.
Multiplayer uses aircraft collision volumes; host-side terrain/building occlusion and
projectile impact on terrain are not yet implemented. Solo radar uses cached terrain LOS.
Combat is currently free-for-all between pilots, without teams or respawning in flight.
Existing LAN/VPN/direct reachability requirements and lobby-code behavior still apply.

VERIFY
Java 21:
  ./gradlew :game:combatChecks :desktop:classes --offline
Checks cover local component damage, independent engines, fire procedure, redundant
utilities, damaged-flight response, numerical stability, actual missile interception,
real GLB fracture/reset/cache isolation, remote replicas and rebasing, and two real TCP
clients with host-confirmed weapons/state. Native renderer screenshots were also checked.
No real account/profile or terrain download is needed by these regression checks.

REFERENCE
Eagle Dynamics F/A-18C guide (avionics/procedure reference, not a claim of equal fidelity):
https://www.digitalcombatsimulator.com/upload/iblock/8d7/2s3e89jqknz7xmti8hrhe1bjt2uw1s3e/DCS%20FA-18C%20Early%20Access%20Guide%20EN.pdf
