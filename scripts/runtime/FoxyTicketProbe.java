package audit;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import me.gallowsdove.foxymachines.listeners.ChunkLoaderPolicyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable CI-only Paper plugin; never packaged into FoxyMachines. */
public final class FoxyTicketProbe extends JavaPlugin {
    private static final int BLOCK_X = 2048;
    private static final int BLOCK_Y = 96;
    private static final int BLOCK_Z = 2048;
    private static final int UNRELATED_FORCE_CHUNK_X = 144;
    private static final int UNRELATED_FORCE_CHUNK_Z = 144;
    private static final UUID OWNER = UUID.fromString("b4b2ea55-8a62-42b3-9b25-b57c255debf7");

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::verify, 110L);
    }

    private void verify() {
        try {
            String phase = Files.readString(Path.of("foxy-policy-phase.txt")).trim();
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                throw new IllegalStateException("Test must have exactly zero online players");
            }
            Plugin foxy = Bukkit.getPluginManager().getPlugin("FoxyMachines");
            if (foxy == null || !foxy.isEnabled()) {
                throw new IllegalStateException("Real FoxyMachines plugin did not enable");
            }
            Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
            if (slimefun == null || !slimefun.isEnabled()) {
                throw new IllegalStateException("Real Slimefun core did not enable");
            }
            World world = Bukkit.getWorlds().get(0);
            Block block = world.getBlockAt(BLOCK_X, BLOCK_Y, BLOCK_Z);
            if (phase.equals("ALWAYS_FIRST")) {
                block.setType(Material.BEACON);
                if (!ChunkLoaderPolicyService.register(block, OWNER)) {
                    throw new IllegalStateException("Failed to persist real new-loader ownership");
                }
                // A separate operator/system vanilla flag must be untouched
                // across the policy changes and the four server restarts.
                world.setChunkForceLoaded(UNRELATED_FORCE_CHUNK_X, UNRELATED_FORCE_CHUNK_Z, true);
            }
            if (block.getType() != Material.BEACON) {
                throw new IllegalStateException("The previously placed beacon was not preserved");
            }
            int cx = BLOCK_X >> 4, cz = BLOCK_Z >> 4;
            boolean hasTicket = world.getPluginChunkTickets(cx, cz).contains(foxy);
            boolean expectTicket = phase.startsWith("ALWAYS");
            if (hasTicket != expectTicket) {
                throw new IllegalStateException("Native chunk ticket mismatch for " + phase
                        + ": expected=" + expectTicket + " actual=" + hasTicket);
            }
            if (world.isChunkForceLoaded(cx, cz)) {
                throw new IllegalStateException("New Foxy-owned loader must not set vanilla force-load");
            }
            if (!world.isChunkForceLoaded(UNRELATED_FORCE_CHUNK_X, UNRELATED_FORCE_CHUNK_Z)) {
                throw new IllegalStateException("Unrelated operator force-loaded chunk was modified");
            }
            Path registry = foxy.getDataFolder().toPath().resolve("chunk-loader-positions.properties");
            if (!Files.isRegularFile(registry)) {
                throw new IllegalStateException("Missing durable loader owner registry");
            }
            String saved = Files.readString(registry);
            if (!saved.contains(OWNER.toString()) || !saved.contains(";" + BLOCK_Y + ";")) {
                throw new IllegalStateException("Owner or coordinates missing across restart");
            }
            SlimefunItem item = SlimefunItem.getById("CHUNK_LOADER");
            if (item == null) {
                throw new IllegalStateException("The real FoxyMachines item was not registered");
            }
            boolean expectedHidden = phase.equals("OFF");
            if (item.isHidden() != expectedHidden) {
                throw new IllegalStateException("Guide visibility mismatch for " + phase
                        + ": expectedHidden=" + expectedHidden + " actual=" + item.isHidden());
            }
            if (ChunkLoaderPolicyService.canPlace() == expectedHidden) {
                throw new IllegalStateException("Placement permission mismatch for " + phase);
            }
            getLogger().info("FOXY_NATIVE_CHUNK_POLICY_PASS " + phase
                    + " ticket=" + hasTicket + " hidden=" + item.isHidden()
                    + " registry=preserved players=0 forceLoaded=false");
        } catch (Throwable failure) {
            getLogger().severe("FOXY_NATIVE_CHUNK_POLICY_FAIL "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            failure.printStackTrace();
        }
    }
}
