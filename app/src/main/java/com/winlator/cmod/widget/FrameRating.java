package com.winlator.cmod.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.ai.AIProfile;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    private Context context;
    private long lastTime = 0;
    private int frameCount = 0;
    private float lastFPS = 0;
    private String totalRAM = null;
    private final TextView tvFPS;
    private final TextView tvRenderer;
    private final TextView tvGPU;
    private final TextView tvRAM;
    private final TextView tvTemperature;
    private final TextView tvBatteryLevel;
    private final TextView tvBatteryPower;
    private HashMap graphicsDriverConfig;
    private LinearLayout layoutContainer;
    private ImageView settingsButton;
    private ImageView settingsButtonStart;
    private SharedPreferences prefs;
    private String prefsKey;
    private float lastX, lastY;
    private boolean isDragging = false;
    private AIProfile aiProfile = null;

    public void setAIProfile(AIProfile aiProfile) {
        this.aiProfile = aiProfile;
    }

    // Элементы для строк (чтобы скрывать всю строку)
    private LinearLayout fpsRow;
    private LinearLayout rendererRow;
    private LinearLayout gpuRow;
    private LinearLayout ramRow;
    private LinearLayout temperatureRow;
    private LinearLayout batteryLevelRow;
    private LinearLayout batteryPowerRow;

    // Настройки
    private boolean showFPS = true;
    private boolean showRenderer = true;
    private boolean showGPU = true;
    private boolean showRAM = true;
    private boolean showTemperature = true;
    private boolean showBatteryLevel = true;
    private boolean showBatteryPower = true;
    private boolean isVerticalLayout = true;
    private int backgroundAlpha = 51; // 20% по умолчанию (0x33)
    private int textAlpha = 255; // 100% по умолчанию
    private int buttonAlpha = 128; // 50% по умолчанию для кнопки
    private int fpsLimit = 0; // 0 = без ограничения
    private boolean showMangoHud = true; // Новая настройка

    public FrameRating(Context context, HashMap graphicsDriverConfig) {
        this(context, graphicsDriverConfig, null);
    }

    public FrameRating(Context context, HashMap graphicsDriverConfig, AttributeSet attrs) {
        this(context, graphicsDriverConfig, attrs, 0);
    }

    public FrameRating(Context context, HashMap graphicsDriverConfig, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        this.graphicsDriverConfig = graphicsDriverConfig;
        
        // Инициализация SharedPreferences
        prefs = context.getSharedPreferences("frame_rating_prefs", Context.MODE_PRIVATE);
        prefsKey = "frame_rating_position_" + context.getPackageName();
        
        // Загрузка настроек
        loadSettings();
        
        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        
        // Находим все элементы
        fpsRow = view.findViewById(R.id.FPSRow);
        rendererRow = view.findViewById(R.id.RendererRow);
        gpuRow = view.findViewById(R.id.GPURow);
        ramRow = view.findViewById(R.id.RAMRow);
        temperatureRow = view.findViewById(R.id.TemperatureRow);
        batteryLevelRow = view.findViewById(R.id.BatteryLevelRow);
        batteryPowerRow = view.findViewById(R.id.BatteryPowerRow);
        
        tvFPS = view.findViewById(R.id.TVFPS);
        tvRenderer = view.findViewById(R.id.TVRenderer);
        tvGPU = view.findViewById(R.id.TVGPU);
        tvRAM = view.findViewById(R.id.TVRAM);
        tvTemperature = view.findViewById(R.id.TVTemperature);
        tvBatteryLevel = view.findViewById(R.id.TVBatteryLevel);
        tvBatteryPower = view.findViewById(R.id.TVBatteryPower);
        
        tvRenderer.setText("OpenGL");
        tvGPU.setText(GPUInformation.getRenderer(graphicsDriverConfig.get("version").toString(), context));
        totalRAM = getTotalRAM();
        
        layoutContainer = view.findViewById(R.id.LLContainer);
        settingsButton = view.findViewById(R.id.IVSettings);
        settingsButtonStart = view.findViewById(R.id.IVSettingsStart);
        
        addView(view);
        
        // Инициализация кнопок настроек и drag & drop
        initSettingsButtons();
        
        // Применение настроек
        applySettings();
    }
    
    private void initSettingsButtons() {
        // Сделать кнопки настроек всегда видимыми
        settingsButton.setVisibility(View.VISIBLE);
        settingsButtonStart.setVisibility(View.VISIBLE);
        
        // Настройка drag & drop для обеих кнопок настроек
        View.OnTouchListener dragListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        isDragging = false;
                        return true; // Захватываем событие для кнопок настроек
                        
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - lastX;
                        float deltaY = event.getRawY() - lastY;
                        
                        if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                            isDragging = true;
                        }
                        
                        if (isDragging) {
                            // Перемещаем весь FrameRating
                            setX(getX() + deltaX);
                            setY(getY() + deltaY);
                            
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                        }
                        return true; // Захватываем событие при drag
                        
                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            savePosition();
                            isDragging = false;
                            return true; // Захватываем событие после drag
                        } else {
                            // Одиночное нажатие - открыть настройки
                            showSettingsDialog();
                            return true; // Захватываем событие для кнопок настроек
                        }
                }
                return false; // Пропускаем другие события
            }
        };
        
        settingsButton.setOnTouchListener(dragListener);
        settingsButtonStart.setOnTouchListener(dragListener);
        
        // Разрешаем обработку касаний для основного контента - события будут проходить сквозь
        layoutContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Пропускаем события касания для основного контента
                // чтобы они доходили до приложения под FrameRating
                return false;
            }
        });
        
        // Устанавливаем обработчик для самого FrameRating чтобы события проходили сквозь
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Пропускаем все события касания через FrameRating
                // чтобы можно было взаимодействовать с приложением под ним
                return false;
            }
        });
    }
    
    private void loadSavedPosition() {
        String position = prefs.getString(prefsKey, null);
        if (position != null) {
            String[] coords = position.split(",");
            if (coords.length == 2) {
                setX(Float.parseFloat(coords[0]));
                setY(Float.parseFloat(coords[1]));
            }
        } else {
            // Позиция по умолчанию
            setX(dpToPx(20));
            setY(dpToPx(20));
        }
    }
    
    private void savePosition() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(prefsKey, getX() + "," + getY());
        editor.apply();
    }
    
    private void loadSettings() {
        showFPS = prefs.getBoolean("show_fps", true);
        showRenderer = prefs.getBoolean("show_renderer", true);
        showGPU = prefs.getBoolean("show_gpu", true);
        showRAM = prefs.getBoolean("show_ram", true);
        showTemperature = prefs.getBoolean("show_temperature", true);
        showBatteryLevel = prefs.getBoolean("show_battery_level", true);
        showBatteryPower = prefs.getBoolean("show_battery_power", true);
        isVerticalLayout = prefs.getBoolean("vertical_layout", true);
        backgroundAlpha = prefs.getInt("background_alpha", 51);
        textAlpha = prefs.getInt("text_alpha", 255);
        buttonAlpha = prefs.getInt("button_alpha", 128);
        fpsLimit = prefs.getInt("fps_limit", 0);
        showMangoHud = prefs.getBoolean("show_mangohud", true);
    }
    
    public void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("show_fps", showFPS);
        editor.putBoolean("show_renderer", showRenderer);
        editor.putBoolean("show_gpu", showGPU);
        editor.putBoolean("show_ram", showRAM);
        editor.putBoolean("show_temperature", showTemperature);
        editor.putBoolean("show_battery_level", showBatteryLevel);
        editor.putBoolean("show_battery_power", showBatteryPower);
        editor.putBoolean("vertical_layout", isVerticalLayout);
        editor.putInt("background_alpha", backgroundAlpha);
        editor.putInt("text_alpha", textAlpha);
        editor.putInt("button_alpha", buttonAlpha);
        editor.putInt("fps_limit", fpsLimit);
        editor.putBoolean("show_mangohud", showMangoHud);
        editor.apply();
        
        applySettings();
        
        // Обновляем конфигурацию MangoHud
        updateMangoHudConfig();
    }
    
    public void applySettings() {
        // Применяем видимость элементов (скрываем всю строку)
        fpsRow.setVisibility(showFPS ? View.VISIBLE : View.GONE);
        rendererRow.setVisibility(showRenderer ? View.VISIBLE : View.GONE);
        gpuRow.setVisibility(showGPU ? View.VISIBLE : View.GONE);
        ramRow.setVisibility(showRAM ? View.VISIBLE : View.GONE);
        temperatureRow.setVisibility(showTemperature ? View.VISIBLE : View.GONE);
        batteryLevelRow.setVisibility(showBatteryLevel ? View.VISIBLE : View.GONE);
        batteryPowerRow.setVisibility(showBatteryPower ? View.VISIBLE : View.GONE);
        
        // Применяем ориентацию layout
        if (layoutContainer != null) {
            layoutContainer.setOrientation(isVerticalLayout ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            
            // Для горизонтального layout настраиваем отступы
            if (!isVerticalLayout) {
                for (int i = 0; i < layoutContainer.getChildCount(); i++) {
                    View child = layoutContainer.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        LinearLayout row = (LinearLayout) child;
                        if (i > 0) {
                            row.setPadding(dpToPx(4), row.getPaddingTop(), row.getPaddingRight(), row.getPaddingBottom());
                        }
                    }
                }
            }
        }
        
        // Применяем прозрачность фона
        applyBackgroundAlpha();
        
        // Применяем прозрачность текста
        applyTextAlpha();
        
        // Применяем прозрачность кнопок
        applyButtonAlpha();
        
        // Загружаем сохраненную позицию после применения настроек
        loadSavedPosition();
    }
    
    private void applyBackgroundAlpha() {
        int[] rows = {R.id.FPSRow, R.id.RendererRow, R.id.GPURow, R.id.RAMRow, 
                     R.id.TemperatureRow, R.id.BatteryLevelRow, R.id.BatteryPowerRow};
        
        for (int rowId : rows) {
            View row = findViewById(rowId);
            if (row != null && row.getVisibility() == View.VISIBLE) {
                int color = 0x000000; // Черный цвет
                int alphaColor = (backgroundAlpha << 24) | (color & 0x00FFFFFF);
                row.setBackgroundColor(alphaColor);
            }
        }
    }
    
    private void applyTextAlpha() {
        int textColor = (textAlpha << 24) | 0x00FFFFFF; // Белый текст с альфой
        
        // Применяем прозрачность ко ВСЕМ текстовым элементам
        tvFPS.setTextColor(textColor);
        tvRenderer.setTextColor(textColor);
        tvGPU.setTextColor(textColor);
        tvRAM.setTextColor(textColor);
        tvTemperature.setTextColor(textColor);
        tvBatteryLevel.setTextColor(textColor);
        tvBatteryPower.setTextColor(textColor);
        
        // Для цветных заголовков сохраняем цвет, но применяем альфу
        applyColoredTextAlpha();
    }
    
    private void applyColoredTextAlpha() {
        int[] labelColors = {
            0xFF0277BD, // FPS - синий
            0xFFFC0303, // Renderer - красный  
            0xFF5C23A6, // GPU - фиолетовый
            0xFF23A6A4, // RAM - бирюзовый
            0xFFFF9800, // Temperature - оранжевый
            0xFF4CAF50, // Battery Level - зеленый
            0xFF9C27B0  // Battery Power - фиолетовый
        };
        
        int[] rowIds = {R.id.FPSRow, R.id.RendererRow, R.id.GPURow, R.id.RAMRow,
                       R.id.TemperatureRow, R.id.BatteryLevelRow, R.id.BatteryPowerRow};
        
        for (int i = 0; i < rowIds.length; i++) {
            View row = findViewById(rowIds[i]);
            if (row != null && row.getVisibility() == View.VISIBLE && row instanceof LinearLayout) {
                LinearLayout linearRow = (LinearLayout) row;
                if (linearRow.getChildCount() >= 2) {
                    View labelView = linearRow.getChildAt(0);
                    if (labelView instanceof TextView && i < labelColors.length) {
                        int color = labelColors[i];
                        int alphaColor = (textAlpha << 24) | (color & 0x00FFFFFF);
                        ((TextView) labelView).setTextColor(alphaColor);
                    }
                    
                    // Также применяем прозрачность к значениям (второй TextView в строке)
                    View valueView = linearRow.getChildAt(1);
                    if (valueView instanceof TextView) {
                        int valueColor = (textAlpha << 24) | 0x00FFFFFF;
                        ((TextView) valueView).setTextColor(valueColor);
                    }
                }
            }
        }
    }
    
    private void applyButtonAlpha() {
        if (settingsButton != null) {
            int buttonBgColor = (buttonAlpha << 24) | 0x000000; // Черный фон с альфой
            settingsButton.setBackgroundColor(buttonBgColor);
        }
        if (settingsButtonStart != null) {
            int buttonBgColor = (buttonAlpha << 24) | 0x000000; // Черный фон с альфой
            settingsButtonStart.setBackgroundColor(buttonBgColor);
        }
    }
    
    private void showSettingsDialog() {
        FrameRatingSettingsDialog dialog = new FrameRatingSettingsDialog(context, this);
        dialog.show();
    }
    
    private String getTotalRAM() {
        String totalRAM = "";
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        totalRAM = StringUtils.formatBytes(memoryInfo.totalMem);
        return totalRAM;
    }
    
    private String getAvailableRAM() {
        String availableRAM = "";
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
        availableRAM = StringUtils.formatBytes(usedMem, false);
        return availableRAM;
    }
    
    /**
     * Получает температуру CPU в градусах Цельсия
     */
    private float getCPUTemperature() {
        try {
            // Попробуем несколько возможных путей к файлам температуры
            String[] thermalPaths = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp",
                "/sys/devices/virtual/thermal/thermal_zone1/temp"
            };
            
            for (String path : thermalPaths) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(path));
                    String line = reader.readLine();
                    reader.close();
                    
                    if (line != null && !line.trim().isEmpty()) {
                        float temp = Float.parseFloat(line.trim());
                        // Если температура в миллиградусах, конвертируем в градусы
                        if (temp > 1000) {
                            temp = temp / 1000.0f;
                        }
                        return temp;
                    }
                } catch (Exception e) {
                    // Продолжаем пробовать следующий путь
                }
            }
        } catch (Exception e) {
            Log.e("FrameRating", "Error reading CPU temperature", e);
        }
        return 0.0f;
    }
    
    /**
     * Получает температуру батареи в градусах Цельсия
     */
    private float getBatteryTemperature() {
        try {
            // Используем Intent для получения информации о батарее
            android.content.Intent batteryIntent = getContext().registerReceiver(null, 
                new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int temperature = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
                return temperature / 10.0f; // Конвертируем из десятых градусов
            }
        } catch (Exception e) {
            Log.e("FrameRating", "Error reading battery temperature", e);
        }
        return 0.0f;
    }
    
    /**
     * Получает уровень заряда батареи в процентах
     */
    private int getBatteryLevel() {
        try {
            android.content.Intent batteryIntent = getContext().registerReceiver(null, 
                new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (level * 100) / scale;
                }
            }
        } catch (Exception e) {
            Log.e("FrameRating", "Error reading battery level", e);
        }
        return -1;
    }
    
    /**
     * Получает текущую мощность батареи в ваттах (расход/заряд)
     */
    private float getBatteryPower() {
        try {
            android.content.Intent batteryIntent = getContext().registerReceiver(null, 
                new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int voltage = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
                
                // Пробуем разные способы получения тока
                int current = 0;
                
                // Способ 1: Прямое получение через известные константы
                try {
                    current = batteryIntent.getIntExtra("current_now", 0);
                    if (current == 0) {
                        current = batteryIntent.getIntExtra("batterycurrent", 0);
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
                
                // Способ 2: Используем BatteryManager если доступен (API 21+)
                if (current == 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        android.os.BatteryManager bm = (android.os.BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
                        current = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    } catch (Exception e) {
                        Log.d("FrameRating", "BatteryManager not available");
                    }
                }
                
                // Способ 3: Альтернативные ключи Intent
                if (current == 0) {
                    // Пробуем другие возможные ключи
                    String[] currentKeys = {"current_now", "batterycurrent", "current", "now_current"};
                    for (String key : currentKeys) {
                        try {
                            current = batteryIntent.getIntExtra(key, 0);
                            if (current != 0) break;
                        } catch (Exception e) {
                            // Продолжаем пробовать следующий ключ
                        }
                    }
                }
                
                if (voltage > 0 && current != 0) {
                    // Конвертируем милливольты в вольты и микроамперы в амперы
                    float voltageV = voltage / 1000.0f;
                    float currentA = Math.abs(current) / 1000000.0f; // Берем абсолютное значение
                    
                    // Определяем знак (заряд или разряд)
                    boolean isCharging = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
                    float power = currentA * voltageV;
                    
                    // Возвращаем мощность с правильным знаком
                    return isCharging ? power : -power;
                }
            }
        } catch (Exception e) {
            Log.e("FrameRating", "Error reading battery power", e);
        }
        return 0.0f;
    }
    
    /**
     * Обновляет конфигурационный файл MangoHud с текущими настройками FPS лимита
     */
    private void updateMangoHudConfig() {
        try {
            // Создаем конфигурационный файл MangoHud
            String configContent = generateMangoHudConfig();
            String configPath = context.getFilesDir().getPath() + "/mangohud.conf";
            
            java.io.FileWriter writer = new java.io.FileWriter(configPath);
            writer.write(configContent);
            writer.close();
            
            Log.d("MangoHud", "Config file updated. showMangoHud: " + showMangoHud + ", fpsLimit: " + fpsLimit);
            Log.d("MangoHud", "Config content:\n" + configContent);
            
            // Отправляем сигнал для обновления конфигурации
            sendMangoHudReloadSignal();
            
        } catch (Exception e) {
            Log.e("FrameRating", "Error updating MangoHud config", e);
        }
    }
    
    /**
     * Генерирует содержимое конфигурационного файла MangoHud
     */
    private String generateMangoHudConfig() {
        StringBuilder config = new StringBuilder();
        
        // Настройка ограничения FPS (работает всегда)
        if (fpsLimit > 0) {
            config.append("fps_limit=").append(fpsLimit).append("\n");
        } else {
            config.append("fps_limit=0\n"); // Без ограничения
        }
        
        // Базовые настройки MangoHud
        if (!showMangoHud) {
            // Если отображение отключено, делаем полностью прозрачный фрейм
            config.append("no_display=false\n"); // Оставляем отображение включенным
            config.append("background_alpha=0.0\n"); // Полностью прозрачный фон
            config.append("text_alpha=0.0\n"); // Полностью прозрачный текст
            config.append("position=top-left\n");
            
            // Отключаем все элементы отображения
            config.append("fps=0\n");
            config.append("frametime=0\n");
            config.append("cpu_stats=0\n");
            config.append("gpu_stats=0\n");
            config.append("ram_stats=0\n");
            config.append("vram=0\n");
            config.append("engine_version=0\n");
            config.append("gpu_name=0\n");
            config.append("core_load=0\n");
            config.append("io_read=0\n");
            config.append("io_write=0\n");
            config.append("arch=0\n");
            config.append("frame_timing=0\n");
            config.append("media_player=0\n");
            config.append("time=0\n");
            config.append("version=0\n");
            config.append("histogram=0\n");
            config.append("graphs=0\n");
        } else {
            // Если отображение включено, показываем нормальный оверлей
            config.append("no_display=false\n");
            config.append("background_alpha=0.4\n");
            config.append("position=top-left\n");
            config.append("text_scale=1.0\n");
            config.append("text_alpha=1.0\n");
            
            // Включаем основные элементы отображения
            config.append("fps=1\n");
            config.append("frametime=1\n");
            config.append("cpu_stats=1\n");
            config.append("gpu_stats=1\n");
            config.append("ram_stats=1\n");
            config.append("vram=1\n");
        }
        
        return config.toString();
    }
    
    /**
     * Отправляет сигнал для перезагрузки конфигурации MangoHud
     */
    private void sendMangoHudReloadSignal() {
        try {
            // Логируем текущие настройки для отладки
            Log.d("MangoHud", "Sending reload signal. showMangoHud: " + showMangoHud + ", fpsLimit: " + fpsLimit);
            
            // Пробуем несколько способов отправки сигнала
            String[] commands = {
                "pkill -USR1 mangohud",
                "pkill -USR1 -f mangohud",
                "killall -USR1 mangohud"
            };
            
            boolean signalSent = false;
            for (String command : commands) {
                try {
                    java.lang.Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        Log.d("MangoHud", "Reload signal sent successfully with command: " + command);
                        signalSent = true;
                        break;
                    }
                } catch (Exception e) {
                    Log.d("MangoHud", "Failed to send signal with command: " + command + ", error: " + e.getMessage());
                }
            }
            
            // Если не удалось отправить сигнал через pkill, пробуем через процесс игры
            if (!signalSent) {
                GuestProgramLauncherComponent.sendMangoHudReloadSignal();
            }
            
        } catch (Exception e) {
            Log.e("FrameRating", "Error sending MangoHud reload signal", e);
        }
    }

    public void setRenderer(String renderer) {
        tvRenderer.setText(renderer);
    }

    public void setGpuName(String gpuName) {
        tvGPU.setText(gpuName);
    }

    public void reset() {
        tvRenderer.setText("OpenGL");
        tvGPU.setText(GPUInformation.getRenderer(graphicsDriverConfig.get("version").toString(), context));
    }

    public void update() {
        if (lastTime == 0) lastTime = SystemClock.elapsedRealtime();
        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            if (aiProfile != null) aiProfile.addFpsSample(lastFPS);
            post(this);
            lastTime = time;
            frameCount = 0;
        }
        frameCount++;
    }

    @Override
    public void run() {
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        if (showFPS) {
            tvFPS.setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));
        }
        if (showRAM) {
            tvRAM.setText(getAvailableRAM() + " GB Used / " + totalRAM + " Total");
        }
        if (showTemperature) {
            float cpuTemp = getCPUTemperature();
            float batteryTemp = getBatteryTemperature();
            String tempText = String.format(Locale.ENGLISH, "Bat🔋%.0f°C/CPU🔲%.0f°C", batteryTemp, cpuTemp);
            tvTemperature.setText(tempText);
        }
        if (showBatteryLevel) {
            int batteryLevel = getBatteryLevel();
            if (batteryLevel >= 0) {
                tvBatteryLevel.setText(String.format(Locale.ENGLISH, "%d%%", batteryLevel));
            } else {
                tvBatteryLevel.setText("N/A");
            }
        }
        if (showBatteryPower) {
            float batteryPower = getBatteryPower();
            String sign = batteryPower > 0 ? "+" : (batteryPower < 0 ? "-" : "");
            String powerText;
            if (batteryPower == 0) {
                powerText = "0.00W";
            } else {
                powerText = String.format(Locale.ENGLISH, "%s%.2fW", sign, Math.abs(batteryPower));
            }
            tvBatteryPower.setText(powerText);
        }
    }
    
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
    
    // Геттеры для настроек
    public boolean isShowFPS() { return showFPS; }
    public boolean isShowRenderer() { return showRenderer; }
    public boolean isShowGPU() { return showGPU; }
    public boolean isShowRAM() { return showRAM; }
    public boolean isShowTemperature() { return showTemperature; }
    public boolean isShowBatteryLevel() { return showBatteryLevel; }
    public boolean isShowBatteryPower() { return showBatteryPower; }
    public boolean isVerticalLayout() { return isVerticalLayout; }
    public int getBackgroundAlpha() { return backgroundAlpha; }
    public int getTextAlpha() { return textAlpha; }
    public int getButtonAlpha() { return buttonAlpha; }
    public int getFpsLimit() { return fpsLimit; }
    public boolean isShowMangoHud() { return showMangoHud; }
    
    // Сеттеры для настроек
    public void setShowFPS(boolean show) { this.showFPS = show; }
    public void setShowRenderer(boolean show) { this.showRenderer = show; }
    public void setShowGPU(boolean show) { this.showGPU = show; }
    public void setShowRAM(boolean show) { this.showRAM = show; }
    public void setShowTemperature(boolean show) { this.showTemperature = show; }
    public void setShowBatteryLevel(boolean show) { this.showBatteryLevel = show; }
    public void setShowBatteryPower(boolean show) { this.showBatteryPower = show; }
    public void setVerticalLayout(boolean vertical) { this.isVerticalLayout = vertical; }
    public void setBackgroundAlpha(int alpha) { this.backgroundAlpha = alpha; }
    public void setTextAlpha(int alpha) { this.textAlpha = alpha; }
    public void setButtonAlpha(int alpha) { this.buttonAlpha = alpha; }
    public void setFpsLimit(int limit) { 
        this.fpsLimit = limit; 
        updateMangoHudConfig(); // Автоматически обновляем конфигурацию при изменении лимита
    }
    public void setShowMangoHud(boolean show) { 
        this.showMangoHud = show; 
        updateMangoHudConfig(); // Автоматически обновляем конфигурацию
    }
}