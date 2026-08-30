package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * Intégration optionnelle avec HUDEngine.
 *
 * <p>Cette classe génère et affiche une mini-map graphique
 * représentant les claims autour du joueur.</p>
 *
 * <ul>
 *     <li>Vert : claim du joueur</li>
 *     <li>Rouge : claim d'un autre joueur</li>
 *     <li>Gris : terrain non claim</li>
 *     <li>Bleu : position du joueur</li>
 * </ul>
 */
public final class HudEngineIntegration {

    /* ========================================================= */
    /* CONSTANTES                                                 */
    /* ========================================================= */

    public static final String HUD_KEY = "noxoclaim:minimap";

    private static final String HUD_ENGINE_PLUGIN = "HUDEngine";

    private static final String PROVIDER_CLASS =
            "io.github.nacvark.hudengine.api.HudEngineProvider";

    private static final int MAP_SIZE = 11;
    private static final int MAP_RADIUS = MAP_SIZE / 2;

    private static final int CELL_SIZE = 12;
    private static final int MAP_PIXELS = MAP_SIZE * CELL_SIZE;

    private static final int FRAME_PADDING = 4;

    private static final int MIN_REFRESH_TICKS = 2;
    private static final long DEFAULT_REFRESH_TICKS = 5L;

    /* ========================================================= */
    /* CHAMPS                                                      */
    /* ========================================================= */

    private final NoxoClaim plugin;

    private Object engine;

    private boolean ready;

    private int refreshTask = -1;

    /* ========================================================= */
    /* CONSTRUCTEUR                                                 */
    /* ========================================================= */

    public HudEngineIntegration(NoxoClaim plugin) {
        this.plugin = plugin;
    }

    /* ========================================================= */
    /* CYCLE DE VIE                                                 */
    /* ========================================================= */

    /**
     * Démarre l'intégration HUDEngine.
     */
    public void start() {
        stopRefreshTask();

        ready = false;
        engine = null;

        if (!plugin.getConfig().getBoolean("hudengine.enabled", true)) {
            plugin.getLogger().info(
                    "HUDEngine : intégration désactivée dans la configuration."
            );
            return;
        }

        Bukkit.getScheduler().runTaskLater(
                plugin,
                this::resolveSafely,
                20L
        );
    }

    /**
     * Arrête l'intégration HUDEngine.
     */
    public void stop() {
        stopRefreshTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            hide(player);
        }

        ready = false;
        engine = null;
    }

    /**
     * Recherche et initialise HUDEngine.
     */
    private void resolveSafely() {
        try {
            var hudPlugin = Bukkit
                    .getPluginManager()
                    .getPlugin(HUD_ENGINE_PLUGIN);

            if (hudPlugin == null || !hudPlugin.isEnabled()) {
                plugin.getLogger().info(
                        "HUDEngine : non disponible. "
                                + "NoxoClaim continue sans HUD."
                );
                return;
            }

            Class<?> providerClass = Class.forName(
                    PROVIDER_CLASS,
                    false,
                    hudPlugin.getClass().getClassLoader()
            );

            Method findMethod = providerClass.getMethod("find");

            Object result = findMethod.invoke(null);

            if (!(result instanceof Optional<?> optional)
                    || optional.isEmpty()) {

                plugin.getLogger().warning(
                        "HUDEngine : fournisseur API introuvable."
                );
                return;
            }

            engine = optional.get();

            ensureMinimapAssets(
                    hudPlugin.getDataFolder().toPath()
            );

            invokePublicApiQuietly(engine, "reload");

            registerValues();

            if (!hasPublicApiMethod(engine, "values")) {
                plugin.getLogger().warning(
                        "HUDEngine : API values() indisponible."
                );

                engine = null;
                return;
            }

            ready = true;

            plugin.getLogger().info(
                    "HUDEngine : mini-map graphique NoxoClaim prête ("
                            + MAP_SIZE
                            + "x"
                            + MAP_SIZE
                            + ")."
            );

            for (Player player : Bukkit.getOnlinePlayers()) {
                show(player);
            }

            startRefreshTask();

        } catch (ClassNotFoundException exception) {

            plugin.getLogger().info(
                    "HUDEngine : API absente, intégration désactivée."
            );

        } catch (Throwable throwable) {

            ready = false;
            engine = null;

            plugin.getLogger().warning(
                    "HUDEngine : intégration désactivée après erreur : "
                            + rootMessage(throwable)
            );
        }
    }

    /* ========================================================= */
    /* REFRESH                                                      */
    /* ========================================================= */

    private void startRefreshTask() {
        stopRefreshTask();

        long refreshTicks = Math.max(
                MIN_REFRESH_TICKS,
                plugin.getConfig().getLong(
                        "hudengine.refresh-ticks",
                        DEFAULT_REFRESH_TICKS
                )
        );

        refreshTask = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (!isReady()) {
                                return;
                            }

                            for (Player player : Bukkit.getOnlinePlayers()) {
                                refresh(player);
                            }
                        },
                        refreshTicks,
                        refreshTicks
                )
                .getTaskId();
    }

    private void stopRefreshTask() {
        if (refreshTask == -1) {
            return;
        }

        Bukkit.getScheduler().cancelTask(refreshTask);
        refreshTask = -1;
    }

    /* ========================================================= */
    /* VALEURS HUD                                                  */
    /* ========================================================= */

    /**
     * Enregistre les 121 cellules de la mini-map auprès de HUDEngine.
     */
    private void registerValues() {
        try {
            Object values = invokePublicApi(
                    engine,
                    "values"
            );

            Method registerMethod = findPublicApiMethod(
                    values,
                    "register",
                    String.class,
                    Function.class
            );

            if (registerMethod == null) {
                throw new NoSuchMethodException(
                        "values.register(String, Function)"
                );
            }

            for (int screenZ = -MAP_RADIUS;
                 screenZ <= MAP_RADIUS;
                 screenZ++) {

                for (int screenX = -MAP_RADIUS;
                     screenX <= MAP_RADIUS;
                     screenX++) {

                    final int x = screenX;
                    final int z = screenZ;

                    registerMethod.invoke(
                            values,
                            cellKey(x, z),
                            (Function<Player, String>)
                                    player -> cellState(player, x, z)
                    );
                }
            }

        } catch (Throwable throwable) {

            plugin.getLogger().warning(
                    "HUDEngine : enregistrement des valeurs impossible : "
                            + rootMessage(throwable)
            );
        }
    }

    /**
     * Retourne l'état d'une cellule de la mini-map.
     *
     * @return 0 = libre
     *         1 = claim du joueur
     *         2 = claim d'un autre joueur
     */
    private String cellState(
            Player player,
            int screenX,
            int screenZ
    ) {
        if (player == null || !player.isOnline()) {
            return "0";
        }

        var location = player.getLocation();

        int[] relative = rotateRelative(
                screenX,
                screenZ,
                location.getYaw()
        );

        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;

        Claim claim = plugin.claims().atChunk(
                player.getWorld().getName(),
                centerX + relative[0],
                centerZ + relative[1]
        );

        if (claim == null) {
            return "0";
        }

        return claim.getOwner().equals(player.getUniqueId())
                ? "1"
                : "2";
    }

    /**
     * Convertit les coordonnées écran de la mini-map
     * en coordonnées relatives au monde selon la rotation du joueur.
     */
    private int[] rotateRelative(
            int screenX,
            int screenZ,
            float yaw
    ) {
        double radians = Math.toRadians(-yaw);

        int worldX = (int) Math.round(
                screenX * Math.cos(radians)
                        - screenZ * Math.sin(radians)
        );

        int worldZ = (int) Math.round(
                screenX * Math.sin(radians)
                        + screenZ * Math.cos(radians)
        );

        return new int[]{
                worldX,
                worldZ
        };
    }

    /* ========================================================= */
    /* ASSETS                                                       */
    /* ========================================================= */

    /**
     * Génère les ressources nécessaires à la mini-map.
     */
    private void ensureMinimapAssets(Path hudEngineData)
            throws IOException {

        Path images = hudEngineData.resolve("images");
        Path layouts = hudEngineData.resolve("layouts");
        Path huds = hudEngineData.resolve("huds");

        Files.createDirectories(images);
        Files.createDirectories(layouts);
        Files.createDirectories(huds);

        writeMapSprite(
                images.resolve("noxoclaim-cell.png")
        );

        writePlayerSprite(
                images.resolve("noxoclaim-player.png")
        );

        writeFrameSprite(
                images.resolve("noxoclaim-frame.png")
        );

        writeImageDefinitions(
                images.resolve("noxoclaim-minimap.yml")
        );

        writeLayout(
                layouts.resolve("noxoclaim-minimap.yml")
        );

        writeHud(
                huds.resolve("noxoclaim-minimap.yml")
        );
    }

    /**
     * Génère le sprite contenant les trois états d'une cellule.
     */
    private void writeMapSprite(Path file)
            throws IOException {

        BufferedImage image = new BufferedImage(
                CELL_SIZE * 3,
                CELL_SIZE,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = image.createGraphics();

        try {
            Color[] colors = {
                    new Color(35, 35, 35, 210),
                    new Color(45, 190, 80, 245),
                    new Color(220, 65, 65, 245)
            };

            for (int state = 0; state < 3; state++) {
                int x = state * CELL_SIZE;

                graphics.setColor(colors[state]);
                graphics.fillRect(
                        x,
                        0,
                        CELL_SIZE,
                        CELL_SIZE
                );

                graphics.setColor(
                        new Color(255, 255, 255, 75)
                );

                graphics.setStroke(
                        new BasicStroke(1f)
                );

                graphics.drawRect(
                        x,
                        0,
                        CELL_SIZE - 1,
                        CELL_SIZE - 1
                );
            }

        } finally {
            graphics.dispose();
        }

        ImageIO.write(
                image,
                "png",
                file.toFile()
        );
    }

    /**
     * Génère le marqueur du joueur.
     */
    private void writePlayerSprite(Path file)
            throws IOException {

        BufferedImage image = new BufferedImage(
                CELL_SIZE,
                CELL_SIZE,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setColor(
                    new Color(45, 140, 255, 255)
            );

            graphics.fillRect(
                    0,
                    0,
                    CELL_SIZE,
                    CELL_SIZE
            );

            graphics.setColor(Color.WHITE);

            graphics.setStroke(
                    new BasicStroke(2f)
            );

            graphics.drawLine(
                    6,
                    1,
                    6,
                    11
            );

            graphics.drawLine(
                    1,
                    6,
                    11,
                    6
            );

        } finally {
            graphics.dispose();
        }

        ImageIO.write(
                image,
                "png",
                file.toFile()
        );
    }

    /**
     * Génère le cadre de la mini-map.
     */
    private void writeFrameSprite(Path file)
            throws IOException {

        int size = MAP_PIXELS + (FRAME_PADDING * 2);

        BufferedImage image = new BufferedImage(
                size,
                size,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setColor(
                    new Color(0, 0, 0, 175)
            );

            graphics.fillRect(
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight()
            );

            graphics.setColor(
                    new Color(255, 255, 255, 150)
            );

            graphics.setStroke(
                    new BasicStroke(2f)
            );

            graphics.drawRect(
                    1,
                    1,
                    image.getWidth() - 3,
                    image.getHeight() - 3
            );

        } finally {
            graphics.dispose();
        }

        ImageIO.write(
                image,
                "png",
                file.toFile()
        );
    }

    /* ========================================================= */
    /* CONFIGURATION DES IMAGES                                    */
    /* ========================================================= */

    private void writeImageDefinitions(Path file)
            throws IOException {

        StringBuilder output = new StringBuilder();

        output.append("noxoclaim-frame:\n")
                .append("  file: noxoclaim-frame.png\n")
                .append("  setting:\n")
                .append("    scale: 1\n\n");

        output.append("noxoclaim-player:\n")
                .append("  file: noxoclaim-player.png\n")
                .append("  setting:\n")
                .append("    scale: 1\n\n");

        for (int z = -MAP_RADIUS;
             z <= MAP_RADIUS;
             z++) {

            for (int x = -MAP_RADIUS;
                 x <= MAP_RADIUS;
                 x++) {

                output.append(
                                imageKey(x, z)
                        )
                        .append(":\n")
                        .append("  file: noxoclaim-cell.png\n")
                        .append("  type: listener\n")
                        .append("  split: 3\n")
                        .append("  split-type: left\n")
                        .append("  setting:\n")
                        .append("    scale: 1\n")
                        .append("    listener:\n")
                        .append("      value: \"")
                        .append(cellKey(x, z))
                        .append("\"\n")
                        .append("      max: \"2\"\n\n");
            }
        }

        Files.writeString(
                file,
                output.toString()
        );
    }

    /* ========================================================= */
    /* LAYOUT                                                       */
    /* ========================================================= */

    private void writeLayout(Path file)
            throws IOException {

        StringBuilder output = new StringBuilder();

        output.append("noxoclaim-minimap:\n")
                .append("  x: -140\n")
                .append("  y: 4\n")
                .append("  images:\n")
                .append("    1:\n")
                .append("      name: noxoclaim-frame\n")
                .append("      x: 0\n")
                .append("      y: 0\n")
                .append("      layer: 0\n");

        int imageId = 10;

        for (int z = -MAP_RADIUS;
             z <= MAP_RADIUS;
             z++) {

            for (int x = -MAP_RADIUS;
                 x <= MAP_RADIUS;
                 x++) {

                output.append("    ")
                        .append(imageId++)
                        .append(":\n")
                        .append("      name: ")
                        .append(imageKey(x, z))
                        .append("\n")
                        .append("      x: ")
                        .append((x + MAP_RADIUS) * CELL_SIZE + FRAME_PADDING)
                        .append("\n")
                        .append("      y: ")
                        .append((z + MAP_RADIUS) * CELL_SIZE + FRAME_PADDING)
                        .append("\n")
                        .append("      layer: 1\n");
            }
        }

        /*
         * Marqueur du joueur au centre de la mini-map.
         */
        output.append("    200:\n")
                .append("      name: noxoclaim-player\n")
                .append("      x: ")
                .append(FRAME_PADDING + MAP_RADIUS * CELL_SIZE)
                .append("\n")
                .append("      y: ")
                .append(FRAME_PADDING + MAP_RADIUS * CELL_SIZE)
                .append("\n")
                .append("      layer: 3\n");

        /*
         * Pas de section "texts".
         *
         * La mini-map est entièrement graphique.
         * Cela évite les références vers des textes inexistants
         * qui pourraient faire échouer la validation HUDEngine.
         */
        Files.writeString(
                file,
                output.toString()
        );
    }

    /* ========================================================= */
    /* HUD                                                          */
    /* ========================================================= */

    private void writeHud(Path file)
            throws IOException {

        String content =
                "noxoclaim:minimap:\n"
                        + "  layouts:\n"
                        + "    1:\n"
                        + "      name: noxoclaim-minimap\n"
                        + "      x: 99\n"
                        + "      y: 5\n";

        Files.writeString(
                file,
                content
        );
    }

    /* ========================================================= */
    /* IDENTIFIANTS                                                 */
    /* ========================================================= */

    private static String cellKey(
            int x,
            int z
    ) {
        return "noxoclaim:cell_"
                + (x + MAP_RADIUS)
                + "_"
                + (z + MAP_RADIUS);
    }

    private static String imageKey(
            int x,
            int z
    ) {
        return "noxoclaim_cell_"
                + (x + MAP_RADIUS)
                + "_"
                + (z + MAP_RADIUS);
    }

    /* ========================================================= */
    /* API PUBLIQUE                                                 */
    /* ========================================================= */

    public boolean isReady() {
        return ready && engine != null;
    }

    public void show(Player player) {
        invokePlayerAction(player, "show");
    }

    public void hide(Player player) {
        invokePlayerAction(player, "hide");
    }

    public void refresh(Player player) {
        invokePlayerAction(player, "refresh");
    }

    public void refreshAll() {
        if (!isReady()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /* ========================================================= */
    /* ACTIONS JOUEUR                                               */
    /* ========================================================= */

    private void invokePlayerAction(
            Player player,
            String action
    ) {
        if (!isReady()
                || player == null
                || !player.isOnline()) {

            return;
        }

        try {
            Object controller = invokePublicApi(
                    engine,
                    "player",
                    Player.class,
                    player
            );

            if (controller == null) {
                return;
            }

            if ("refresh".equals(action)) {

                invokePublicApi(
                        controller,
                        "refresh"
                );

                return;
            }

            invokePublicApi(
                    controller,
                    action,
                    String.class,
                    HUD_KEY
            );

            invokePublicApiQuietly(
                    controller,
                    "refresh"
            );

        } catch (Throwable throwable) {

            plugin.getLogger().fine(
                    "HUDEngine "
                            + action
                            + " impossible : "
                            + rootMessage(throwable)
            );
        }
    }

    /* ========================================================= */
    /* RÉFLEXION                                                    */
    /* ========================================================= */

    private static boolean hasPublicApiMethod(
            Object target,
            String name,
            Class<?>... parameterTypes
    ) {
        return findPublicApiMethod(
                target,
                name,
                parameterTypes
        ) != null;
    }

    private static Method findPublicApiMethod(
            Object target,
            String name,
            Class<?>... parameterTypes
    ) {
        if (target == null) {
            return null;
        }

        Method method = findInInterfaces(
                target.getClass(),
                name,
                parameterTypes
        );

        if (method != null) {
            return method;
        }

        Class<?> superclass =
                target.getClass().getSuperclass();

        while (superclass != null) {

            method = findInInterfaces(
                    superclass,
                    name,
                    parameterTypes
            );

            if (method != null) {
                return method;
            }

            superclass = superclass.getSuperclass();
        }

        return null;
    }

    private static Method findInInterfaces(
            Class<?> type,
            String name,
            Class<?>... parameterTypes
    ) {
        for (Class<?> interfaceClass : type.getInterfaces()) {

            try {
                Method method = interfaceClass.getMethod(
                        name,
                        parameterTypes
                );

                if (Modifier.isPublic(
                        interfaceClass.getModifiers()
                )) {
                    return method;
                }

            } catch (NoSuchMethodException ignored) {
                // On continue la recherche dans les interfaces parentes.
            }

            Method nested = findInInterfaces(
                    interfaceClass,
                    name,
                    parameterTypes
            );

            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    private static Object invokePublicApi(
            Object target,
            String name,
            Class<?> parameterType,
            Object argument
    ) throws Exception {

        Method method = findPublicApiMethod(
                target,
                name,
                parameterType
        );

        if (method == null) {
            throw new NoSuchMethodException(name);
        }

        return method.invoke(
                target,
                argument
        );
    }

    private static Object invokePublicApi(
            Object target,
            String name
    ) throws Exception {

        Method method = findPublicApiMethod(
                target,
                name
        );

        if (method == null) {
            throw new NoSuchMethodException(name);
        }

        return method.invoke(target);
    }

    private static void invokePublicApiQuietly(
            Object target,
            String name
    ) {
        try {
            invokePublicApi(
                    target,
                    name
            );
        } catch (Throwable ignored) {
            // Méthode optionnelle : aucune action nécessaire.
        }
    }

    /* ========================================================= */
    /* UTILITAIRES                                                  */
    /* ========================================================= */

    private static String rootMessage(
            Throwable throwable
    ) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
