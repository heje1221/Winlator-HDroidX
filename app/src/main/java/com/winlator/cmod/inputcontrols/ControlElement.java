package com.winlator.cmod.inputcontrols;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

import com.winlator.cmod.core.CubicBezierInterpolator;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class ControlElement {
    public static final float STICK_DEAD_ZONE = 0.15f;
    public static final float DPAD_DEAD_ZONE = 0.3f;
    public static final float STICK_SENSITIVITY = 3.0f;
    public static final float TRACKPAD_MIN_SPEED = 0.8f;
    public static final float TRACKPAD_MAX_SPEED = 20.0f;
    public static final byte TRACKPAD_ACCELERATION_THRESHOLD = 4;
    public static final short BUTTON_MIN_TIME_TO_KEEP_PRESSED = 300;
    
    // DYNAMIC_STICK specific constants
    public static final float DEFAULT_ACTIVATION_RADIUS = 100.0f;
    public static final float MIN_ACTIVATION_RADIUS = 50.0f;
    public static final float MAX_ACTIVATION_RADIUS = 600.0f; // Увеличено с 300 до 600
    
    // New constants for rectangular activation zone
    public static final float DEFAULT_ACTIVATION_ZONE_WIDTH = 200.0f;
    public static final float DEFAULT_ACTIVATION_ZONE_HEIGHT = 200.0f;
    public static final float MIN_ACTIVATION_ZONE_SIZE = 50.0f;
    public static final float MAX_ACTIVATION_ZONE_SIZE = 600.0f; // Увеличено с 500 до 600
    
    // Profile switching constants
    public static final float MIN_SWITCH_DELAY = 0.0f;
    public static final float MAX_SWITCH_DELAY = 3.0f;
    public static final float DEFAULT_SWITCH_DELAY = 0.0f;
    
    // MultiBinding constants
    private static final int MAX_MULTI_BINDINGS = 4;
    
    // Vertical Scroll Bar constants
    public static final float DEFAULT_SCROLLBAR_WIDTH = 20.0f;
    public static final float DEFAULT_SCROLLBAR_HEIGHT = 200.0f;
    public static final float MIN_SCROLLBAR_WIDTH = 10.0f;
    public static final float MAX_SCROLLBAR_WIDTH = 100.0f;
    public static final float MIN_SCROLLBAR_HEIGHT = 100.0f;
    public static final float MAX_SCROLLBAR_HEIGHT = 600.0f;
    public static final float DEFAULT_SCROLL_SENSITIVITY = 2.0f;
    public static final float MIN_SCROLL_SENSITIVITY = 0.5f;
    public static final float MAX_SCROLL_SENSITIVITY = 5.0f;
    
    // Scroll acceleration constants
    private static final float SCROLL_ACCELERATION_THRESHOLD = 0.1f; // Порог для ускоренной прокрутки
    private static final float SCROLL_ACCELERATION_MULTIPLIER = 3.0f; // Множитель ускоренной прокрутки
    private static final float INFINITE_SCROLL_THRESHOLD = 0.05f; // Порог для бесконечной прокрутки
    
    // Add field for custom icon support
    private String customIconId;
    private boolean hasCustomIcon = false;
    
    // Add field for icon size multiplier
    private float iconSizeMultiplier = 1.0f;
    
    // Add fields for opacity controls
    private float buttonOpacity = 0.7f; // 70% opacity by default
    private float iconOpacity = 1.0f;   // 100% opacity by default
    
    // Add field for outline visibility
    private boolean showOutline = true; // Show outline by default
    
    // Profile switching fields
    private boolean enableProfileSwitching = false;
    private int targetProfileId = 0;
    private float switchDelay = DEFAULT_SWITCH_DELAY;
    private boolean profileSwitchTriggered = false;
    
    // DYNAMIC_STICK specific fields
    private float stickActivationRadius = DEFAULT_ACTIVATION_RADIUS;
    private boolean stickActive = false;
    private PointF stickTouchOrigin = new PointF();
    private PointF stickCurrentPosition = new PointF();
    private final RectF activationZone = new RectF();
    private boolean stickVisible = false;
    
    // New fields for activation zone visualization and rectangular zone
    private boolean showActivationZone = true;
    private float activationZoneWidth = DEFAULT_ACTIVATION_ZONE_WIDTH;
    private float activationZoneHeight = DEFAULT_ACTIVATION_ZONE_HEIGHT;
    
    // MultiBinding fields
    private MultiBinding[] multiBindings = new MultiBinding[MAX_MULTI_BINDINGS];
    private boolean useMultiBinding = false;
    
    // Vertical Scroll Bar specific fields
    private float scrollBarWidth = DEFAULT_SCROLLBAR_WIDTH;
    private float scrollBarHeight = DEFAULT_SCROLLBAR_HEIGHT;
    private float scrollSensitivity = DEFAULT_SCROLL_SENSITIVITY;
    private float currentScrollOffset = 0.5f; // Start centered
    private float scrollTouchStartY = 0.0f;
    private float accumulatedScrollDelta = 0.0f; // Накопленная дельта для плавной прокрутки
    
    public enum Type {
        BUTTON, D_PAD, RANGE_BUTTON, STICK, TRACKPAD, DYNAMIC_STICK, VERTICAL_SCROLL_BAR;

        public static String[] names() {
            Type[] types = values();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
            return names;
        }
    }
    
    public enum Shape {
        CIRCLE, RECT, ROUND_RECT, SQUARE;

        public static String[] names() {
            Shape[] shapes = values();
            String[] names = new String[shapes.length];
            for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
            return names;
        }
    }
    
    public enum Range {
        FROM_A_TO_Z(26), FROM_0_TO_9(10), FROM_F1_TO_F12(12), FROM_NP0_TO_NP9(10);
        public final byte max;

        Range(int max) {
            this.max = (byte)max;
        }

        public static String[] names() {
            Range[] ranges = values();
            String[] names = new String[ranges.length];
            for (int i = 0; i < ranges.length; i++) names[i] = ranges[i].name().replace("_", " ");
            return names;
        }
    }
    
    private final InputControlsView inputControlsView;
    private Type type = Type.BUTTON;
    private Shape shape = Shape.CIRCLE;
    private Binding[] bindings = {Binding.NONE, Binding.NONE, Binding.NONE, Binding.NONE};
    private float scale = 1.0f;
    private short x;
    private short y;
    private boolean selected = false;
    private boolean toggleSwitch = false;
    private int currentPointerId = -1;
    private final Rect boundingBox = new Rect();
    private boolean[] states = new boolean[4];
    private boolean boundingBoxNeedsUpdate = true;
    private String text = "";
    private byte iconId;
    private Range range;
    private byte orientation;
    private PointF currentPosition;
    private RangeScroller scroller;
    private CubicBezierInterpolator interpolator;
    private Object touchTime;

    private final PointF touchDownOrigin = new PointF();

    public ControlElement(InputControlsView inputControlsView) {
        this.inputControlsView = inputControlsView;
        initializeMultiBindings();
    }
    
    public ControlElement(InputControlsView inputControlsView, JSONObject elementJSONObject) {
        this.inputControlsView = inputControlsView;
        initializeMultiBindings();
        loadFromJSON(elementJSONObject);
    }

    private void initializeMultiBindings() {
        for (int i = 0; i < MAX_MULTI_BINDINGS; i++) {
            multiBindings[i] = new MultiBinding();
        }
    }

    private void loadFromJSON(JSONObject elementJSONObject) {
        try {
            type = Type.valueOf(elementJSONObject.getString("type"));
            shape = Shape.valueOf(elementJSONObject.getString("shape"));
            
            JSONArray bindingsJSONArray = elementJSONObject.getJSONArray("bindings");
            bindings = new Binding[bindingsJSONArray.length()];
            for (int i = 0; i < bindingsJSONArray.length(); i++) {
                bindings[i] = Binding.valueOf(bindingsJSONArray.getString(i));
            }
            
            scale = (float)elementJSONObject.getDouble("scale");
            x = (short)(elementJSONObject.getDouble("x") * inputControlsView.getMaxWidth());
            y = (short)(elementJSONObject.getDouble("y") * inputControlsView.getMaxHeight());
            toggleSwitch = elementJSONObject.getBoolean("toggleSwitch");
            text = elementJSONObject.getString("text");
            iconId = (byte)elementJSONObject.getInt("iconId");
            
            // Load profile switching settings
            if (elementJSONObject.has("enableProfileSwitching")) {
                enableProfileSwitching = elementJSONObject.getBoolean("enableProfileSwitching");
            }
            if (elementJSONObject.has("targetProfileId")) {
                targetProfileId = elementJSONObject.getInt("targetProfileId");
            }
            if (elementJSONObject.has("switchDelay")) {
                switchDelay = (float)elementJSONObject.getDouble("switchDelay");
            }
            
            // Load DYNAMIC_STICK specific settings
            if (type == Type.DYNAMIC_STICK) {
                if (elementJSONObject.has("stickActivationRadius")) {
                    stickActivationRadius = (float)elementJSONObject.getDouble("stickActivationRadius");
                }
                
                // Load new activation zone settings
                if (elementJSONObject.has("showActivationZone")) {
                    showActivationZone = elementJSONObject.getBoolean("showActivationZone");
                }
                if (elementJSONObject.has("activationZoneWidth")) {
                    activationZoneWidth = (float)elementJSONObject.getDouble("activationZoneWidth");
                }
                if (elementJSONObject.has("activationZoneHeight")) {
                    activationZoneHeight = (float)elementJSONObject.getDouble("activationZoneHeight");
                }
            }
            
            // Load Vertical Scroll Bar specific settings
            if (type == Type.VERTICAL_SCROLL_BAR) {
                if (elementJSONObject.has("scrollBarWidth")) {
                    scrollBarWidth = (float)elementJSONObject.getDouble("scrollBarWidth");
                }
                if (elementJSONObject.has("scrollBarHeight")) {
                    scrollBarHeight = (float)elementJSONObject.getDouble("scrollBarHeight");
                }
                if (elementJSONObject.has("scrollSensitivity")) {
                    scrollSensitivity = (float)elementJSONObject.getDouble("scrollSensitivity");
                }
            }
            
            // Load custom icon data
            if (elementJSONObject.has("customIconId")) {
                customIconId = elementJSONObject.getString("customIconId");
                hasCustomIcon = elementJSONObject.getBoolean("hasCustomIcon");
            }
            
            // Load icon size multiplier
            if (elementJSONObject.has("iconSizeMultiplier")) {
                iconSizeMultiplier = (float)elementJSONObject.getDouble("iconSizeMultiplier");
            }
            
            // Load opacity settings
            if (elementJSONObject.has("buttonOpacity")) {
                buttonOpacity = (float)elementJSONObject.getDouble("buttonOpacity");
            }
            if (elementJSONObject.has("iconOpacity")) {
                iconOpacity = (float)elementJSONObject.getDouble("iconOpacity");
            }
            
            // Load outline visibility
            if (elementJSONObject.has("showOutline")) {
                showOutline = elementJSONObject.getBoolean("showOutline");
            }
            
            // Load MultiBinding settings
            if (elementJSONObject.has("useMultiBinding")) {
                useMultiBinding = elementJSONObject.getBoolean("useMultiBinding");
                if (useMultiBinding && elementJSONObject.has("multiBindings")) {
                    JSONArray multiBindingsArray = elementJSONObject.getJSONArray("multiBindings");
                    for (int i = 0; i < multiBindingsArray.length() && i < MAX_MULTI_BINDINGS; i++) {
                        multiBindings[i] = MultiBinding.fromJSONObject(multiBindingsArray.getJSONObject(i));
                    }
                }
            }
            
            if (type == Type.RANGE_BUTTON) {
                range = Range.valueOf(elementJSONObject.getString("range"));
                orientation = elementJSONObject.has("orientation") ? (byte)elementJSONObject.getInt("orientation") : 0;
                scroller = new RangeScroller(inputControlsView, this);
            }
            
            states = new boolean[bindings.length];
            boundingBoxNeedsUpdate = true;
            
            // Initialize activation zone for DYNAMIC_STICK
            if (type == Type.DYNAMIC_STICK) {
                updateActivationZone();
            }
        }
        catch (JSONException e) {
            reset();
        }
    }

    private void reset() {
        setBinding(Binding.NONE);
        scroller = null;

        if (type == Type.D_PAD || type == Type.STICK || type == Type.DYNAMIC_STICK) {
            bindings[0] = Binding.KEY_W;
            bindings[1] = Binding.KEY_D;
            bindings[2] = Binding.KEY_S;
            bindings[3] = Binding.KEY_A;
        }
        else if (type == Type.TRACKPAD) {
            bindings[0] = Binding.MOUSE_MOVE_UP;
            bindings[1] = Binding.MOUSE_MOVE_RIGHT;
            bindings[2] = Binding.MOUSE_MOVE_DOWN;
            bindings[3] = Binding.MOUSE_MOVE_LEFT;
        }
        else if (type == Type.VERTICAL_SCROLL_BAR) {
            bindings[0] = Binding.MOUSE_SCROLL_UP_CONTINUOUS;
            bindings[1] = Binding.MOUSE_SCROLL_DOWN_CONTINUOUS;
            // Initialize scroll position to center
            currentScrollOffset = 0.5f;
        }
        else if (type == Type.RANGE_BUTTON) {
            scroller = new RangeScroller(inputControlsView, this);
        }

        text = "";
        iconId = 0;
        range = null;
        boundingBoxNeedsUpdate = true;
        
        // Reset custom icon state
        customIconId = null;
        hasCustomIcon = false;
        
        // Reset icon size multiplier
        iconSizeMultiplier = 1.0f;
        
        // Reset opacity to defaults
        buttonOpacity = 0.7f;
        iconOpacity = 1.0f;
        
        // Reset outline visibility to default
        showOutline = true;
        
        // Reset profile switching settings
        enableProfileSwitching = false;
        targetProfileId = 0;
        switchDelay = DEFAULT_SWITCH_DELAY;
        profileSwitchTriggered = false;
        
        // Reset DYNAMIC_STICK specific settings
        stickActivationRadius = DEFAULT_ACTIVATION_RADIUS;
        stickActive = false;
        stickVisible = false;
        stickTouchOrigin.set(0, 0);
        stickCurrentPosition.set(0, 0);
        
        // Reset new activation zone settings
        showActivationZone = true;
        activationZoneWidth = DEFAULT_ACTIVATION_ZONE_WIDTH;
        activationZoneHeight = DEFAULT_ACTIVATION_ZONE_HEIGHT;
        
        // Reset MultiBinding settings
        useMultiBinding = false;
        for (int i = 0; i < MAX_MULTI_BINDINGS; i++) {
            multiBindings[i].clear();
        }
        
        // Reset Vertical Scroll Bar settings
        scrollBarWidth = DEFAULT_SCROLLBAR_WIDTH;
        scrollBarHeight = DEFAULT_SCROLLBAR_HEIGHT;
        scrollSensitivity = DEFAULT_SCROLL_SENSITIVITY;
        currentScrollOffset = 0.5f;
        scrollTouchStartY = 0.0f;
        accumulatedScrollDelta = 0.0f;
        
        updateActivationZone();
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
        reset();
    }

    // Profile switching methods
    public boolean isEnableProfileSwitching() {
        return enableProfileSwitching;
    }

    public void setEnableProfileSwitching(boolean enableProfileSwitching) {
        this.enableProfileSwitching = enableProfileSwitching;
    }

    public int getTargetProfileId() {
        return targetProfileId;
    }

    public void setTargetProfileId(int targetProfileId) {
        this.targetProfileId = targetProfileId;
    }

    public float getSwitchDelay() {
        return switchDelay;
    }

    public void setSwitchDelay(float switchDelay) {
        this.switchDelay = Math.max(MIN_SWITCH_DELAY, Math.min(MAX_SWITCH_DELAY, switchDelay));
    }

    public boolean isProfileSwitchTriggered() {
        return profileSwitchTriggered;
    }

    public void setProfileSwitchTriggered(boolean profileSwitchTriggered) {
        this.profileSwitchTriggered = profileSwitchTriggered;
    }

    // DYNAMIC_STICK specific methods
    public float getStickActivationRadius() {
        return stickActivationRadius;
    }

    public void setStickActivationRadius(float stickActivationRadius) {
        this.stickActivationRadius = Math.max(MIN_ACTIVATION_RADIUS, Math.min(MAX_ACTIVATION_RADIUS, stickActivationRadius));
        updateActivationZone();
    }

    public boolean isStickActive() {
        return stickActive;
    }

    public void setStickActive(boolean stickActive) {
        this.stickActive = stickActive;
    }

    public boolean isStickVisible() {
        return stickVisible;
    }

    public void setStickVisible(boolean stickVisible) {
        this.stickVisible = stickVisible;
    }

    public PointF getStickTouchOrigin() {
        return stickTouchOrigin;
    }

    public void setStickTouchOrigin(float x, float y) {
        this.stickTouchOrigin.set(x, y);
    }

    public PointF getStickCurrentPosition() {
        return stickCurrentPosition;
    }

    public void setStickCurrentPosition(float x, float y) {
        this.stickCurrentPosition.set(x, y);
    }

    public RectF getActivationZone() {
        return activationZone;
    }

    // New methods for activation zone visualization and rectangular zone
    public boolean isShowActivationZone() {
        return showActivationZone;
    }

    public void setShowActivationZone(boolean showActivationZone) {
        this.showActivationZone = showActivationZone;
    }

    public float getActivationZoneWidth() {
        return activationZoneWidth;
    }

    public void setActivationZoneWidth(float activationZoneWidth) {
        this.activationZoneWidth = Math.max(MIN_ACTIVATION_ZONE_SIZE, Math.min(MAX_ACTIVATION_ZONE_SIZE, activationZoneWidth));
        updateActivationZone();
    }

    public float getActivationZoneHeight() {
        return activationZoneHeight;
    }

    public void setActivationZoneHeight(float activationZoneHeight) {
        this.activationZoneHeight = Math.max(MIN_ACTIVATION_ZONE_SIZE, Math.min(MAX_ACTIVATION_ZONE_SIZE, activationZoneHeight));
        updateActivationZone();
    }

    // MultiBinding methods
    public MultiBinding getMultiBindingAt(int index) {
        if (index >= multiBindings.length) {
            multiBindings = Arrays.copyOf(multiBindings, index + 1);
            for (int i = multiBindings.length - 1; i >= 0; i--) {
                if (multiBindings[i] == null) multiBindings[i] = new MultiBinding();
            }
        }
        return multiBindings[index];
    }

    public void setMultiBindingAt(int index, MultiBinding multiBinding) {
        if (index >= multiBindings.length) {
            multiBindings = Arrays.copyOf(multiBindings, index + 1);
            for (int i = multiBindings.length - 1; i >= 0; i--) {
                if (multiBindings[i] == null) multiBindings[i] = new MultiBinding();
            }
        }
        multiBindings[index] = multiBinding;
    }

    public boolean isUseMultiBinding() {
        return useMultiBinding;
    }

    public void setUseMultiBinding(boolean useMultiBinding) {
        this.useMultiBinding = useMultiBinding;
    }

    // Vertical Scroll Bar methods
    public float getScrollBarWidth() {
        return scrollBarWidth;
    }

    public void setScrollBarWidth(float scrollBarWidth) {
        this.scrollBarWidth = Math.max(MIN_SCROLLBAR_WIDTH, Math.min(MAX_SCROLLBAR_WIDTH, scrollBarWidth));
        boundingBoxNeedsUpdate = true;
    }

    public float getScrollBarHeight() {
        return scrollBarHeight;
    }

    public void setScrollBarHeight(float scrollBarHeight) {
        this.scrollBarHeight = Math.max(MIN_SCROLLBAR_HEIGHT, Math.min(MAX_SCROLLBAR_HEIGHT, scrollBarHeight));
        boundingBoxNeedsUpdate = true;
    }

    public float getScrollSensitivity() {
        return scrollSensitivity;
    }

    public void setScrollSensitivity(float scrollSensitivity) {
        this.scrollSensitivity = Math.max(MIN_SCROLL_SENSITIVITY, Math.min(MAX_SCROLL_SENSITIVITY, scrollSensitivity));
    }

    public float getCurrentScrollOffset() {
        return currentScrollOffset;
    }

    public void setCurrentScrollOffset(float currentScrollOffset) {
        this.currentScrollOffset = currentScrollOffset;
    }

    private void updateActivationZone() {
        if (type != Type.DYNAMIC_STICK) return;
        
        Rect bb = getBoundingBox();
        float centerX = bb.centerX();
        float centerY = bb.centerY();
        
        // Use rectangular activation zone with separate width/height settings
        activationZone.set(
            centerX - activationZoneWidth / 2,
            centerY - activationZoneHeight / 2,
            centerX + activationZoneWidth / 2,
            centerY + activationZoneHeight / 2
        );
    }

    // Add getter for currentPointerId
    public int getCurrentPointerId() {
        return currentPointerId;
    }

    public int getBindingCount() {
        return bindings.length;
    }

    public void setBindingCount(int bindingCount) {
        bindings = new Binding[bindingCount];
        setBinding(Binding.NONE);
        states = new boolean[bindingCount];
        boundingBoxNeedsUpdate = true;
        if (type == Type.DYNAMIC_STICK) {
            updateActivationZone();
        }
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
        boundingBoxNeedsUpdate = true;
        if (type == Type.DYNAMIC_STICK) {
            updateActivationZone();
        }
    }

    public Range getRange() {
        return range != null ? range : Range.FROM_A_TO_Z;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public byte getOrientation() {
        return orientation;
    }

    public void setOrientation(byte orientation) {
        this.orientation = orientation;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isToggleSwitch() {
        return toggleSwitch;
    }

    public void setToggleSwitch(boolean toggleSwitch) {
        this.toggleSwitch = toggleSwitch;
    }
    
    public boolean isShowOutline() {
        return showOutline;
    }
    
    public void setShowOutline(boolean showOutline) {
        this.showOutline = showOutline;
    }

    public Binding getBindingAt(int index) {
        return index < bindings.length ? bindings[index] : Binding.NONE;
    }

    public void setBindingAt(int index, Binding binding) {
        if (index >= bindings.length) {
            int oldLength = bindings.length;
            bindings = Arrays.copyOf(bindings, index+1);
            Arrays.fill(bindings, oldLength-1, bindings.length, Binding.NONE);
            states = new boolean[bindings.length];
            boundingBoxNeedsUpdate = true;
        }
        bindings[index] = binding;
    }

    public void setBinding(Binding binding) {
        Arrays.fill(bindings, binding);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        boundingBoxNeedsUpdate = true;
        if (type == Type.DYNAMIC_STICK) {
            updateActivationZone();
        }
    }

    public short getX() {
        return x;
    }

    public void setX(int x) {
        this.x = (short)x;
        boundingBoxNeedsUpdate = true;
        if (type == Type.DYNAMIC_STICK) {
            updateActivationZone();
        }
    }

    public short getY() {
        return y;
    }

    public void setY(int y) {
        this.y = (short)y;
        boundingBoxNeedsUpdate = true;
        if (type == Type.DYNAMIC_STICK) {
            updateActivationZone();
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public byte getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        this.iconId = (byte)iconId;
    }
    
    public String getCustomIconId() {
        return customIconId;
    }
    
    public void setCustomIconId(String customIconId) {
        this.customIconId = customIconId;
        this.hasCustomIcon = customIconId != null && !customIconId.isEmpty();
    }
    
    public boolean hasCustomIcon() {
        return hasCustomIcon;
    }
    
    public void setHasCustomIcon(boolean hasCustomIcon) {
        this.hasCustomIcon = hasCustomIcon;
        if (!hasCustomIcon) {
            this.customIconId = null;
        }
    }
    
    public float getIconSizeMultiplier() {
        return iconSizeMultiplier;
    }
    
    public void setIconSizeMultiplier(float multiplier) {
        this.iconSizeMultiplier = Math.max(0.1f, Math.min(3.0f, multiplier));
    }
    
    public float getButtonOpacity() {
        return buttonOpacity;
    }
    
    public void setButtonOpacity(float opacity) {
        this.buttonOpacity = Math.max(0.1f, Math.min(1.0f, opacity));
    }
    
    public float getIconOpacity() {
        return iconOpacity;
    }
    
    public void setIconOpacity(float opacity) {
        this.iconOpacity = Math.max(0.1f, Math.min(1.0f, opacity));
    }
    
    public String getId() {
        if (hasCustomIcon && customIconId != null) {
            return customIconId;
        }
        return "element_" + System.identityHashCode(this);
    }

    public Rect getBoundingBox() {
        if (boundingBoxNeedsUpdate) computeBoundingBox();
        return boundingBox;
    }

    private Rect computeBoundingBox() {
        int snappingSize = inputControlsView.getSnappingSize();
        int halfWidth = 0;
        int halfHeight = 0;

        switch (type) {
            case BUTTON:
                switch (shape) {
                    case RECT:
                    case ROUND_RECT:
                        halfWidth = snappingSize * 4;
                        halfHeight = snappingSize * 2;
                        break;
                    case SQUARE:
                        halfWidth = (int)(snappingSize * 2.5f);
                        halfHeight = (int)(snappingSize * 2.5f);
                        break;
                    case CIRCLE:
                        halfWidth = snappingSize * 3;
                        halfHeight = snappingSize * 3;
                        break;
                }
                break;
            case D_PAD: {
                halfWidth = snappingSize * 7;
                halfHeight = snappingSize * 7;
                break;
            }
            case TRACKPAD:
            case STICK:
            case DYNAMIC_STICK: {
                halfWidth = snappingSize * 6;
                halfHeight = snappingSize * 6;
                break;
            }
            case RANGE_BUTTON: {
                halfWidth = snappingSize * ((bindings.length * 4) / 2);
                halfHeight = snappingSize * 2;

                if (orientation == 1) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            }
            case VERTICAL_SCROLL_BAR: {
                halfWidth = (int)(scrollBarWidth * scale / 2);
                halfHeight = (int)(scrollBarHeight * scale / 2);
                break;
            }
        }

        halfWidth *= scale;
        halfHeight *= scale;
        boundingBox.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
        boundingBoxNeedsUpdate = false;
        
        // Update activation zone for DYNAMIC_STICK
        if (type == Type.DYNAMIC_STICK) {
            updateActivationZone();
        }
        
        return boundingBox;
    }

    private String getDisplayText() {
        if (text != null && !text.isEmpty()) {
            return text;
        }
        else if (useMultiBinding && !multiBindings[0].isEmpty()) {
            return multiBindings[0].toString();
        }
        else {
            Binding binding = getBindingAt(0);
            String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
            if (text.length() > 7) {
                String[] parts = text.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) sb.append(part.charAt(0));
                return (binding.isMouse() ? "M" : "")+ sb;
            }
            else return text;
        }
    }

    private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
        final byte testTextSize = 48;
        paint.setTextSize(testTextSize);
        return testTextSize * desiredWidth / paint.measureText(text);
    }

    private static String getRangeTextForIndex(Range range, int index) {
        String text = "";
        switch (range) {
            case FROM_A_TO_Z:
                text = String.valueOf((char)(65 + index));
                break;
            case FROM_0_TO_9:
                text = String.valueOf((index + 1) % 10);
                break;
            case FROM_F1_TO_F12:
                text = "F"+(index + 1);
                break;
            case FROM_NP0_TO_NP9:
                text = "NP"+((index + 1) % 10);
                break;
        }
        return text;
    }

    public void draw(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        int primaryColor = inputControlsView.getPrimaryColor();

        // Apply button opacity to fill color
        int fillColor = ColorUtils.setAlphaComponent(primaryColor, (int)(70 * buttonOpacity));

        paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
        paint.setStyle(Paint.Style.STROKE);
        float strokeWidth = snappingSize * 0.25f;
        paint.setStrokeWidth(strokeWidth);
        Rect boundingBox = getBoundingBox();

        switch (type) {
            case BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();

                if (isEngaged()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    switch (shape) {
                        case CIRCLE:
                            canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                            break;
                        case RECT:
                            canvas.drawRect(boundingBox, paint);
                            break;
                        case ROUND_RECT: {
                            float r = boundingBox.height() * 0.5f;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, r, r, paint);
                            break;
                        }
                        case SQUARE: {
                            float r = snappingSize * 0.75f * scale;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, r, r, paint);
                            break;
                        }
                    }
                }

                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
                    paint.setStrokeWidth(strokeWidth);

                    switch (shape) {
                        case CIRCLE:
                            canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                            break;
                        case RECT:
                            canvas.drawRect(boundingBox, paint);
                            break;
                        case ROUND_RECT: {
                            float r = boundingBox.height() * 0.5f;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, r, r, paint);
                            break;
                        }
                        case SQUARE: {
                            float r = snappingSize * 0.75f * scale;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, r, r, paint);
                            break;
                        }
                    }
                }

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(primaryColor);

                if (hasCustomIcon()) {
                    drawCustomIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height());
                }
                else if (iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
                }
                else {
                    String text = getDisplayText();
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(primaryColor);
                    canvas.drawText(text, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
            case D_PAD: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float offsetX = snappingSize * 2 * scale;
                float offsetY = snappingSize * 3 * scale;
                float start = snappingSize * scale;
                Path path = inputControlsView.getPath();
                path.reset();

                path.moveTo(cx, cy - start);
                path.lineTo(cx - offsetX, cy - offsetY);
                path.lineTo(cx - offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, cy - offsetY);
                path.close();

                path.moveTo(cx - start, cy);
                path.lineTo(cx - offsetY, cy - offsetX);
                path.lineTo(boundingBox.left, cy - offsetX);
                path.lineTo(boundingBox.left, cy + offsetX);
                path.lineTo(cx - offsetY, cy + offsetX);
                path.close();

                path.moveTo(cx, cy + start);
                path.lineTo(cx - offsetX, cy + offsetY);
                path.lineTo(cx - offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, cy + offsetY);
                path.close();

                path.moveTo(cx + start, cy);
                path.lineTo(cx + offsetY, cy - offsetX);
                path.lineTo(boundingBox.right, cy - offsetX);
                path.lineTo(boundingBox.right, cy + offsetX);
                path.lineTo(cx + offsetY, cy + offsetX);
                path.close();

                if (isEngaged()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    canvas.drawPath(path, paint);
                }

                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
                    paint.setStrokeWidth(strokeWidth);
                    canvas.drawPath(path, paint);
                }
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                int oldColor = paint.getColor();
                float radius = snappingSize * 0.75f * scale;

                if (isEngaged()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                }

                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                }

                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                Path path = inputControlsView.getPath();
                path.reset();

                if (orientation == 0) {
                    float lineTop = boundingBox.top + strokeWidth * 0.5f;
                    float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
                    float startX = boundingBox.left;

                    canvas.save();
                    path.addRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    startX -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        if (showOutline) {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setColor(oldColor);

                            if (startX > boundingBox.left && startX  < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        }
                        String text = getRangeTextForIndex(range, index);

                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }

                    if (showOutline) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);
                    }
                    canvas.restore();
                }
                else {
                    float lineLeft = boundingBox.left + strokeWidth * 0.5f;
                    float lineRight = boundingBox.right - strokeWidth * 0.5f;
                    float startY = boundingBox.top;

                    canvas.save();
                    path.addRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(inputControlsView.getPath());
                    startY -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        if (showOutline) {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setColor(oldColor);

                            if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        }
                        String text = getRangeTextForIndex(range, i);

                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }

                    if (showOutline) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);
                    }
                    canvas.restore();
                }
                break;
            }
            case STICK: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                int oldColor = paint.getColor();

                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
                    canvas.drawCircle(cx, cy, boundingBox.height() * 0.5f, paint);
                }

                float thumbstickX = getCurrentPosition().x;
                float thumbstickY = getCurrentPosition().y;
                short thumbRadius = (short) (snappingSize * 3.5f * scale);

                int engagedAlpha = isEngaged() ? (int)(120 * buttonOpacity) : (int)(50 * buttonOpacity);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, engagedAlpha));
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint);

                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);
                }
                break;
            }
            case DYNAMIC_STICK: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                int oldColor = paint.getColor();

                // Draw activation zone (only in edit mode and when enabled)
                if (editMode && showActivationZone) {
                    // Save original paint state
                    Paint.Style originalStyle = paint.getStyle();
                    float originalStrokeWidth = paint.getStrokeWidth();
                    
                    // Draw semi-transparent fill for better visibility
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(0xFF4CAF50, 30)); // Light green with transparency
                    canvas.drawRect(activationZone, paint);
                    
                    // Draw dashed border for activation zone
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(ColorUtils.setAlphaComponent(0xFF4CAF50, 180)); // Green with higher opacity
                    paint.setStrokeWidth(2f);
                    paint.setPathEffect(new DashPathEffect(new float[]{10, 5}, 0));
                    canvas.drawRect(activationZone, paint);
                    
                    // Remove dash effect for other drawings
                    paint.setPathEffect(null);
                    
                    // Draw activation zone dimensions label
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextSize(snappingSize * 0.8f);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setColor(0xFF4CAF50);
                    String dimensions = (int)activationZoneWidth + "x" + (int)activationZoneHeight;
                    canvas.drawText(dimensions, activationZone.centerX(), activationZone.centerY(), paint);
                    
                    // Restore original paint state
                    paint.setStyle(originalStyle);
                    paint.setStrokeWidth(originalStrokeWidth);
                    paint.setColor(oldColor);
                }

                // Draw outer ring (only if outline is enabled and stick is visible)
                if (showOutline && stickVisible) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
                    paint.setStrokeWidth(strokeWidth);
                    canvas.drawCircle(cx, cy, boundingBox.height() * 0.5f, paint);
                }

                // Draw knob only when visible
                if (stickVisible) {
                    float thumbstickX = stickCurrentPosition.x;
                    float thumbstickY = stickCurrentPosition.y;
                    short thumbRadius = (short) (snappingSize * 3.5f * scale);

                    int engagedAlpha = (int)(120 * buttonOpacity);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(primaryColor, engagedAlpha));
                    canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint);

                    if (showOutline) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);
                        canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);
                    }
                }
                break;
            }
            case TRACKPAD: {
                float radius = boundingBox.height() * 0.15f;

                if (isEngaged()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                }

                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
                    canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                    float offset = strokeWidth * 2.5f;
                    float innerStrokeWidth = strokeWidth * 2;
                    float innerHeight = boundingBox.height() - offset * 2;
                    radius = (innerHeight / boundingBox.height()) * radius - (innerStrokeWidth * 0.5f + strokeWidth * 0.5f);
                    paint.setStrokeWidth(innerStrokeWidth);
                    canvas.drawRoundRect(boundingBox.left + offset, boundingBox.top + offset, boundingBox.right - offset, boundingBox.bottom - offset, radius, radius, paint);
                }
                break;
            }
            case VERTICAL_SCROLL_BAR: {
                int oldColor = paint.getColor();
                float width = boundingBox.width();
                float height = boundingBox.height();

                // Draw scroll bar background
                if (isEngaged()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    canvas.drawRect(boundingBox, paint);
                }

                // Draw outline
                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
                    paint.setStrokeWidth(strokeWidth);
                    canvas.drawRect(boundingBox, paint);
                }

                // Draw gradient to indicate scroll direction
                LinearGradient gradient = new LinearGradient(
                    boundingBox.left, boundingBox.top,
                    boundingBox.left, boundingBox.bottom,
                    new int[]{ColorUtils.setAlphaComponent(primaryColor, 100), 
                             ColorUtils.setAlphaComponent(primaryColor, 50),
                             ColorUtils.setAlphaComponent(primaryColor, 100)},
                    new float[]{0.0f, 0.5f, 1.0f},
                    Shader.TileMode.CLAMP
                );
                paint.setShader(gradient);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(boundingBox, paint);
                paint.setShader(null);

                // Draw scroll position indicator (centered by default)
                float indicatorHeight = Math.max(width * 0.8f, 8f);
                float indicatorY = boundingBox.top + (height - indicatorHeight) * currentScrollOffset;
                
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, 180));
                canvas.drawRect(
                    boundingBox.left + width * 0.2f,
                    indicatorY,
                    boundingBox.right - width * 0.2f,
                    indicatorY + indicatorHeight,
                    paint
                );

                // Draw indicator outline
                if (showOutline) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    paint.setStrokeWidth(strokeWidth * 0.5f);
                    canvas.drawRect(
                        boundingBox.left + width * 0.2f,
                        indicatorY,
                        boundingBox.right - width * 0.2f,
                        indicatorY + indicatorHeight,
                        paint
                    );
                }

                paint.setColor(oldColor);
                break;
            }
        }
    }

    private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId) {
        Paint paint = inputControlsView.getPaint();
        Bitmap icon = inputControlsView.getIcon((byte)iconId);
        if (icon != null) {
            int originalAlpha = paint.getAlpha();
            paint.setAlpha((int)(originalAlpha * iconOpacity));
            
            float maxIconSize = Math.min(width, height) * 0.6f;
            float iconAspectRatio = (float) icon.getWidth() / icon.getHeight();
            
            float scaledWidth, scaledHeight;
            if (iconAspectRatio > 1) {
                scaledWidth = maxIconSize;
                scaledHeight = maxIconSize / iconAspectRatio;
            } else {
                scaledHeight = maxIconSize;
                scaledWidth = maxIconSize * iconAspectRatio;
            }
            
            scaledWidth *= iconSizeMultiplier;
            scaledHeight *= iconSizeMultiplier;
            
            if (scaledWidth > width * 0.9f) {
                scaledWidth = width * 0.9f;
                scaledHeight = scaledWidth / iconAspectRatio;
            }
            if (scaledHeight > height * 0.9f) {
                scaledHeight = height * 0.9f;
                scaledWidth = scaledHeight * iconAspectRatio;
            }
            
            int halfWidth = (int)(scaledWidth / 2);
            int halfHeight = (int)(scaledHeight / 2);

            Rect srcRect = new Rect(0, 0, icon.getWidth(), icon.getHeight());
            Rect dstRect = new Rect((int)(cx - halfWidth), (int)(cy - halfHeight), 
                                   (int)(cx + halfWidth), (int)(cy + halfHeight));
            canvas.drawBitmap(icon, srcRect, dstRect, paint);
            
            paint.setAlpha(originalAlpha);
        }
    }
    
    private void drawCustomIcon(Canvas canvas, float cx, float cy, float width, float height) {
        Paint paint = inputControlsView.getPaint();
        Bitmap customIcon = inputControlsView.getCustomIcon(getId());
        if (customIcon != null) {
            int originalAlpha = paint.getAlpha();
            paint.setAlpha((int)(originalAlpha * iconOpacity));
            
            float maxIconSize = Math.min(width, height) * 0.6f;
            float iconAspectRatio = (float) customIcon.getWidth() / customIcon.getHeight();
            
            float scaledWidth, scaledHeight;
            if (iconAspectRatio > 1) {
                scaledWidth = maxIconSize;
                scaledHeight = maxIconSize / iconAspectRatio;
            } else {
                scaledHeight = maxIconSize;
                scaledWidth = maxIconSize * iconAspectRatio;
            }
            
            scaledWidth *= iconSizeMultiplier;
            scaledHeight *= iconSizeMultiplier;
            
            if (scaledWidth > width * 0.9f) {
                scaledWidth = width * 0.9f;
                scaledHeight = scaledWidth / iconAspectRatio;
            }
            if (scaledHeight > height * 0.9f) {
                scaledHeight = height * 0.9f;
                scaledWidth = scaledHeight * iconAspectRatio;
            }
            
            int halfWidth = (int)(scaledWidth / 2);
            int halfHeight = (int)(scaledHeight / 2);

            Rect srcRect = new Rect(0, 0, customIcon.getWidth(), customIcon.getHeight());
            Rect dstRect = new Rect((int)(cx - halfWidth), (int)(cy - halfHeight), 
                                   (int)(cx + halfWidth), (int)(cy + halfHeight));
            canvas.drawBitmap(customIcon, srcRect, dstRect, paint);
            
            paint.setAlpha(originalAlpha);
        } else {
            if (iconId > 0 && iconId < 17) {
                drawIcon(canvas, cx, cy, width, height, iconId);
            }
        }
    }

    public JSONObject toJSONObject() {
        try {
            JSONObject elementJSONObject = new JSONObject();
            elementJSONObject.put("type", type.name());
            elementJSONObject.put("shape", shape.name());

            JSONArray bindingsJSONArray = new JSONArray();
            for (Binding binding : bindings) bindingsJSONArray.put(binding.name());

            elementJSONObject.put("bindings", bindingsJSONArray);
            elementJSONObject.put("scale", Float.valueOf(scale));
            elementJSONObject.put("x", (float)x / inputControlsView.getMaxWidth());
            elementJSONObject.put("y", (float)y / inputControlsView.getMaxHeight());
            elementJSONObject.put("toggleSwitch", toggleSwitch);
            elementJSONObject.put("text", text);
            elementJSONObject.put("iconId", iconId);
            
            // Add profile switching settings
            elementJSONObject.put("enableProfileSwitching", enableProfileSwitching);
            elementJSONObject.put("targetProfileId", targetProfileId);
            elementJSONObject.put("switchDelay", Float.valueOf(switchDelay));
            
            // Add DYNAMIC_STICK specific settings
            if (type == Type.DYNAMIC_STICK) {
                elementJSONObject.put("stickActivationRadius", Float.valueOf(stickActivationRadius));
                // Add new activation zone settings
                elementJSONObject.put("showActivationZone", showActivationZone);
                elementJSONObject.put("activationZoneWidth", Float.valueOf(activationZoneWidth));
                elementJSONObject.put("activationZoneHeight", Float.valueOf(activationZoneHeight));
            }
            
            // Add Vertical Scroll Bar specific settings
            if (type == Type.VERTICAL_SCROLL_BAR) {
                elementJSONObject.put("scrollBarWidth", Float.valueOf(scrollBarWidth));
                elementJSONObject.put("scrollBarHeight", Float.valueOf(scrollBarHeight));
                elementJSONObject.put("scrollSensitivity", Float.valueOf(scrollSensitivity));
            }
            
            // Add custom icon data to JSON
            if (hasCustomIcon && customIconId != null) {
                elementJSONObject.put("customIconId", customIconId);
                elementJSONObject.put("hasCustomIcon", hasCustomIcon);
            }
            
            // Add icon size multiplier to JSON
            elementJSONObject.put("iconSizeMultiplier", Float.valueOf(iconSizeMultiplier));
            
            // Add opacity settings to JSON
            elementJSONObject.put("buttonOpacity", Float.valueOf(buttonOpacity));
            elementJSONObject.put("iconOpacity", Float.valueOf(iconOpacity));
            
            // Add outline visibility to JSON
            elementJSONObject.put("showOutline", showOutline);

            // Add MultiBinding settings to JSON
            elementJSONObject.put("useMultiBinding", useMultiBinding);
            if (useMultiBinding) {
                JSONArray multiBindingsArray = new JSONArray();
                for (MultiBinding multiBinding : multiBindings) {
                    multiBindingsArray.put(multiBinding.toJSONObject());
                }
                elementJSONObject.put("multiBindings", multiBindingsArray);
            }

            if (type == Type.RANGE_BUTTON && range != null) {
                elementJSONObject.put("range", range.name());
                if (orientation != 0) elementJSONObject.put("orientation", orientation);
            }
            return elementJSONObject;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public boolean containsPoint(float x, float y) {
        return getBoundingBox().contains((int)(x + 0.5f), (int)(y + 0.5f));
    }

    // DYNAMIC_STICK specific method to check if point is in activation zone
    public boolean isInActivationZone(float x, float y) {
        if (type != Type.DYNAMIC_STICK) return false;
        return activationZone.contains(x, y);
    }

    private boolean isKeepButtonPressedAfterMinTime() {
        Binding binding = getBindingAt(0);
        return !toggleSwitch && (binding == Binding.GAMEPAD_BUTTON_L3 || binding == Binding.GAMEPAD_BUTTON_R3);
    }

    public boolean handleTouchDown(int pointerId, float x, float y) {
        if (currentPointerId != -1) return false;

        // DYNAMIC_STICK specific logic - check activation zone first
        if (type == Type.DYNAMIC_STICK) {
            if (isInActivationZone(x, y)) {
                currentPointerId = pointerId;
                stickActive = true;
                stickVisible = true;
                stickTouchOrigin.set(x, y);
                stickCurrentPosition.set(x, y); // Start at touch position
                
                // Force neutral value on first frame
                handleTouchMove(pointerId, x, y);
                inputControlsView.invalidate();
                return true;
            }
            return false;
        }

        // For other types, check bounding box
        if (!containsPoint(x, y)) return false;

        currentPointerId = pointerId;

        if (type == Type.BUTTON || type == Type.RANGE_BUTTON) {
            if (isKeepButtonPressedAfterMinTime()) touchTime = System.currentTimeMillis();

            if (!toggleSwitch || !selected) {
                // Handle MultiBinding if enabled
                if (useMultiBinding && !multiBindings[0].isEmpty()) {
                    inputControlsView.handleMultiBinding(multiBindings[0], true);
                } else {
                    inputControlsView.handleInputEvent(getBindingAt(0), true);
                }
            }

            if (type == Type.RANGE_BUTTON) scroller.handleTouchDown(x, y);

            inputControlsView.invalidate();
            return true;
        }

        if (type == Type.STICK) {
            touchDownOrigin.set(x, y);
            handleTouchMove(pointerId, x, y);
            Rect bb = getBoundingBox();
            setCurrentPosition(bb.centerX(), bb.centerY());
            inputControlsView.invalidate();
            return true;
        }

        if (type == Type.TRACKPAD) {
            if (currentPosition == null) currentPosition = new PointF();
            currentPosition.set(x, y);
        }

        if (type == Type.VERTICAL_SCROLL_BAR) {
            scrollTouchStartY = y;
            accumulatedScrollDelta = 0.0f; // Reset accumulated delta
            inputControlsView.invalidate();
            return true;
        }
        return handleTouchMove(pointerId, x, y);
    }

    public boolean handleTouchMove(int pointerId, float x, float y) {
        if (pointerId == currentPointerId && (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.DYNAMIC_STICK || type == Type.VERTICAL_SCROLL_BAR)) {
            float deltaX, deltaY;
            Rect boundingBox = getBoundingBox();
            float radius = boundingBox.width() * 0.5f;
            TouchpadView touchpadView =  inputControlsView.getTouchpadView();

            if (type == Type.TRACKPAD) {
                if (currentPosition == null) currentPosition = new PointF();
                float[] deltaPoint = touchpadView.computeDeltaPoint(currentPosition.x, currentPosition.y, x, y);
                deltaX = deltaPoint[0];
                deltaY = deltaPoint[1];
                currentPosition.set(x, y);
            }
            else if (type == Type.VERTICAL_SCROLL_BAR) {
                // Calculate vertical movement for scroll bar
                float scrollDeltaY = y - scrollTouchStartY;
                
                // Accumulate scroll delta for smooth continuous scrolling
                accumulatedScrollDelta += scrollDeltaY;
                
                // Calculate normalized scroll position for visual feedback (0.0 to 1.0)
                // Invert the direction so that dragging up scrolls up
                float newScrollOffset = currentScrollOffset - scrollDeltaY / (boundingBox.height() * 2);
                
                // Check if we're near the edges for infinite scrolling
                boolean nearTop = newScrollOffset <= INFINITE_SCROLL_THRESHOLD;
                boolean nearBottom = newScrollOffset >= (1.0f - INFINITE_SCROLL_THRESHOLD);
                
                // Apply acceleration when near edges
                float accelerationMultiplier = 1.0f;
                if (nearTop || nearBottom) {
                    accelerationMultiplier = SCROLL_ACCELERATION_MULTIPLIER;
                    
                    // For infinite scroll, wrap around when reaching edges
                    if (nearTop && scrollDeltaY < 0) {
                        // Reached top and still scrolling up - wrap to bottom
                        newScrollOffset = 1.0f - INFINITE_SCROLL_THRESHOLD;
                    } else if (nearBottom && scrollDeltaY > 0) {
                        // Reached bottom and still scrolling down - wrap to top
                        newScrollOffset = INFINITE_SCROLL_THRESHOLD;
                    }
                }
                
                // Clamp scroll offset to valid range
                currentScrollOffset = Mathf.clamp(newScrollOffset, 0.0f, 1.0f);
                
                // Calculate scroll intensity with acceleration
                float scrollIntensity = Math.min(Math.abs(accumulatedScrollDelta) * scrollSensitivity * 0.01f * accelerationMultiplier, 1.0f);
                
                // Generate continuous scroll events with acceleration
                if (scrollDeltaY < -1.0f) {
                    // Scrolling up (dragging finger upward)
                    inputControlsView.handleInputEvent(getBindingAt(0), true, scrollIntensity);
                    inputControlsView.handleInputEvent(getBindingAt(1), false, 0);
                } else if (scrollDeltaY > 1.0f) {
                    // Scrolling down (dragging finger downward)
                    inputControlsView.handleInputEvent(getBindingAt(1), true, scrollIntensity);
                    inputControlsView.handleInputEvent(getBindingAt(0), false, 0);
                } else {
                    // Stop scrolling when movement is minimal
                    inputControlsView.handleInputEvent(getBindingAt(0), false, 0);
                    inputControlsView.handleInputEvent(getBindingAt(1), false, 0);
                }
                
                // Update touch position for next movement calculation
                scrollTouchStartY = y;
                
                inputControlsView.invalidate();
                return true;
            }
            else {
                // For DYNAMIC_STICK, calculate movement relative to touch origin
                if (type == Type.DYNAMIC_STICK) {
                    float offsetX = x - stickTouchOrigin.x;
                    float offsetY = y - stickTouchOrigin.y;
                    
                    // Constrain movement to activation zone bounds (rectangular)
                    float maxOffsetX = activationZoneWidth / 2;
                    float maxOffsetY = activationZoneHeight / 2;
                    
                    if (Math.abs(offsetX) > maxOffsetX) {
                        offsetX = Math.signum(offsetX) * maxOffsetX;
                    }
                    if (Math.abs(offsetY) > maxOffsetY) {
                        offsetY = Math.signum(offsetY) * maxOffsetY;
                    }
                    
                    // Update current position (stick follows finger within bounds)
                    stickCurrentPosition.set(stickTouchOrigin.x + offsetX, stickTouchOrigin.y + offsetY);
                    
                    // Calculate normalized values (-1 to 1)
                    deltaX = offsetX / maxOffsetX;
                    deltaY = offsetY / maxOffsetY;
                } else {
                    // Original STICK logic
                    float localX = x - boundingBox.left;
                    float localY = y - boundingBox.top;
                    float offsetX = localX - radius;
                    float offsetY = localY - radius;

                    float distance = Mathf.lengthSq(radius - localX, radius - localY);
                    if (distance > radius * radius) {
                        float angle = (float)Math.atan2(offsetY, offsetX);
                        offsetX = (float)(Math.cos(angle) * radius);
                        offsetY = (float)(Math.sin(angle) * radius);
                    }

                    deltaX = Mathf.clamp(offsetX / radius, -1, 1);
                    deltaY = Mathf.clamp(offsetY / radius, -1, 1);

                    float magnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    if (magnitude > 1.0f) {
                        deltaX /= magnitude;
                        deltaY /= magnitude;
                    }
                }
            }

            if (type == Type.STICK || type == Type.DYNAMIC_STICK) {
                final boolean[] states = {deltaY <= -STICK_DEAD_ZONE, deltaX >= STICK_DEAD_ZONE, deltaY >= STICK_DEAD_ZONE, deltaX <= -STICK_DEAD_ZONE};

                for (byte i = 0; i < 4; i++) {
                    float value = (i == 1 || i == 3) ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        value = Mathf.clamp(Math.max(0, Math.abs(value) - 0.01f) * Mathf.sign(value) * STICK_SENSITIVITY, -1, 1);
                        inputControlsView.handleInputEvent(binding, true, value);
                        this.states[i] = true;
                    } else {
                        boolean state = binding.isMouseMove() ? (states[i] || states[(i + 2) % 4]) : states[i];
                        inputControlsView.handleInputEvent(binding, state, value);
                        this.states[i] = state;
                    }
                }

                inputControlsView.invalidate();
            }
            else if (type == Type.TRACKPAD) {
                final boolean[] states = {deltaY <= -TRACKPAD_MIN_SPEED, deltaX >= TRACKPAD_MIN_SPEED, deltaY >= TRACKPAD_MIN_SPEED, deltaX <= -TRACKPAD_MIN_SPEED};
                int cursorDx = 0;
                int cursorDy = 0;

                for (byte i = 0; i < 4; i++) {
                    float value = (i == 1 || i == 3 ? deltaX : deltaY);
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        if (interpolator == null) interpolator = new CubicBezierInterpolator();
                        if (Math.abs(value) > TRACKPAD_ACCELERATION_THRESHOLD) value *= STICK_SENSITIVITY;
                        interpolator.set(0.075f, 0.95f, 0.45f, 0.95f);
                        float interpolatedValue = interpolator.getInterpolation(Math.min(1.0f, Math.abs(value / TRACKPAD_MAX_SPEED)));
                        inputControlsView.handleInputEvent(binding, true, Mathf.clamp(interpolatedValue * Mathf.sign(value), -1, 1));
                        this.states[i] = true;
                    }
                    else {
                        if (Math.abs(value) > TouchpadView.CURSOR_ACCELERATION_THRESHOLD) value *= TouchpadView.CURSOR_ACCELERATION;
                        if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                            cursorDx = Mathf.roundPoint(value);
                        }
                        else if (binding == Binding.MOUSE_MOVE_UP || binding == Binding.MOUSE_MOVE_DOWN) {
                            cursorDy = Mathf.roundPoint(value);
                        }
                        else {
                            inputControlsView.handleInputEvent(binding, states[i], value);
                            this.states[i] = states[i];
                        }
                    }
                }

                if (cursorDx != 0 || cursorDy != 0)  {
                    XServer xServer = inputControlsView.getXServer();
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, cursorDx, cursorDy, 0);
                    else
                        inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
                }
            }
            else {
                final boolean[] states = {deltaY <= -DPAD_DEAD_ZONE, deltaX >= DPAD_DEAD_ZONE, deltaY >= DPAD_DEAD_ZONE, deltaX <= -DPAD_DEAD_ZONE};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                    inputControlsView.handleInputEvent(binding, state, value);
                    this.states[i] = state;
                }
            }

            inputControlsView.invalidate();
            return true;
        }
        else if (pointerId == currentPointerId && type == Type.RANGE_BUTTON) {
            scroller.handleTouchMove(x, y);
            return true;
        }
        else return false;
    }

    public boolean handleTouchUp(int pointerId) {
        if (pointerId != currentPointerId) return false;

        // DYNAMIC_STICK specific logic
        if (type == Type.DYNAMIC_STICK) {
            stickActive = false;
            stickVisible = false;
            for (byte i = 0; i < states.length; i++) {
                if (states[i]) inputControlsView.handleInputEvent(getBindingAt(i), false);
                states[i] = false;
            }
            inputControlsView.invalidate();
            currentPointerId = -1;
            return true;
        }

        if (type == Type.BUTTON || type == Type.RANGE_BUTTON) {
            final Binding binding = getBindingAt(0);
            final long    now     = System.currentTimeMillis();

            if (isKeepButtonPressedAfterMinTime() && touchTime != null) {
                long held  = now - (long) touchTime;
                long delay = Math.max(0, BUTTON_MIN_TIME_TO_KEEP_PRESSED - held);
                touchTime  = null;

                inputControlsView.postDelayed(() -> {
                    // Handle MultiBinding if enabled
                    if (useMultiBinding && !multiBindings[0].isEmpty()) {
                        inputControlsView.handleMultiBinding(multiBindings[0], false);
                    } else {
                        inputControlsView.handleInputEvent(binding, false);
                    }
                    inputControlsView.invalidate();
                }, delay);
            } else {
                if (!toggleSwitch || selected) {
                    // Handle MultiBinding if enabled
                    if (useMultiBinding && !multiBindings[0].isEmpty()) {
                        inputControlsView.handleMultiBinding(multiBindings[0], false);
                    } else {
                        inputControlsView.handleInputEvent(binding, false);
                    }
                }
            }

            if (toggleSwitch) selected = !selected;

            if (type == Type.RANGE_BUTTON) {
                scroller.handleTouchUp();
            }

            currentPointerId = -1;
            inputControlsView.invalidate();
            return true;
        }

        else if (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.VERTICAL_SCROLL_BAR) {
            for (byte i = 0; i < states.length; i++) {
                if (states[i]) inputControlsView.handleInputEvent(getBindingAt(i), false);
                states[i] = false;
            }
            
            // For scroll bar, also stop any active scrolling and reset to center
            if (type == Type.VERTICAL_SCROLL_BAR) {
                inputControlsView.handleInputEvent(getBindingAt(0), false, 0);
                inputControlsView.handleInputEvent(getBindingAt(1), false, 0);
                // Reset scroll position to center when released
                currentScrollOffset = 0.5f;
                accumulatedScrollDelta = 0.0f;
            }

            if (type == Type.STICK || type == Type.VERTICAL_SCROLL_BAR) {
                inputControlsView.invalidate();
            }

            if (currentPosition != null) currentPosition = null;
        }

        currentPointerId = -1;
        inputControlsView.invalidate();
        return true;
    }

    public PointF getCurrentPosition() {
        if (currentPosition == null) {
            currentPosition = new PointF(x, y);
        }
        return currentPosition;
    }

    public void setCurrentPosition(float x, float y) {
        if (currentPosition == null) {
            currentPosition = new PointF();
        }
        currentPosition.set(x, y);
        inputControlsView.invalidate();
    }

    private boolean anyStateActive() {
        for (boolean b : states) if (b) return true;
        return false;
    }

    private boolean isEngaged() {
        if (type == Type.BUTTON || type == Type.RANGE_BUTTON) {
            return currentPointerId != -1 || selected;
        }
        return currentPointerId != -1 || anyStateActive();
    }
    
    // Edit mode accessor
    private boolean editMode = false;
    
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }
    
    public boolean isEditMode() {
        return editMode;
    }
}