package tronka.ordinarydiscordintegration.compat;

import net.luckperms.api.node.Node;

public class LuckPermsHelper {
    public static Node getNode(String name) {
        return Node.builder(name).build();
    }
}
