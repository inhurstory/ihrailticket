package inhurstory.ihrailticket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class CmiWarpReader {

    /**
     * 讀取 CMI Warp 檔案，取得對應地點的傳送座標
     * 讀取傳送點名稱，例如 "IHR_B03"
     * 返回 double[] {x, y, z}
     */
    public static double[] getWarpLocation(String warpName) {
        File file = new File(Bukkit.getServer().getPluginManager().getPlugin("CMI").getDataFolder(), "Saves/Warps.yml");

        if (!file.exists()) {
            Bukkit.getLogger().severe("找不到 Warps.yml 檔案: " + file.getAbsolutePath());
            return null;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (!config.contains(warpName + ".Location")) {
            Bukkit.getLogger().warning("找不到傳送點: " + warpName);
            return null;
        }

        String locStr = config.getString(warpName + ".Location");
        if (locStr == null) return null;

        String[] parts = locStr.split(";");
        if (parts.length < 4) return null;

        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new double[]{x, y, z};
        } catch (NumberFormatException e) {
            Bukkit.getLogger().severe("傳送點座標解析失敗: " + locStr);
            return null;
        }
    }
}
