package cloudify.widget.hp;

import cloudify.widget.api.clouds.ICloudServerStatus;

/**
 * User: eliranm
 * Date: 2/11/14
 * Time: 3:39 PM
 */
public enum HpCloudServerStatus implements ICloudServerStatus {

    PENDING, TERMINATED, SUSPENDED, RUNNING, ERROR, UNRECOGNIZED;

    public String value() {
        return name();
    }

    public static HpCloudServerStatus fromValue(String v) {
        try {
            return valueOf(v.replaceAll("\\(.*", ""));
        } catch (IllegalArgumentException e) {
            return UNRECOGNIZED;
        }
    }
}