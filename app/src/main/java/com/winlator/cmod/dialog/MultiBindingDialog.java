package com.winlator.cmod.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.MultiBinding;

import java.util.ArrayList;
import java.util.List;

public class MultiBindingDialog extends DialogFragment {
    private MultiBinding multiBinding;
    private OnMultiBindingSetListener listener;
    private LinearLayout bindingsContainer;
    private List<Binding> currentBindings = new ArrayList<>();
    
    public interface OnMultiBindingSetListener {
        void onMultiBindingSet(MultiBinding multiBinding);
    }
    
    public static MultiBindingDialog newInstance(MultiBinding multiBinding) {
        MultiBindingDialog dialog = new MultiBindingDialog();
        dialog.multiBinding = multiBinding != null ? multiBinding : new MultiBinding();
        return dialog;
    }
    
    public void setOnMultiBindingSetListener(OnMultiBindingSetListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.multi_binding_dialog, null);
        
        initializeUI(view);
        
        builder.setView(view)
                .setTitle("Multi-Action Binding")
                .setPositiveButton("Save", (dialog, which) -> saveMultiBinding())
                .setNegativeButton("Cancel", (dialog, which) -> dismiss());
        
        return builder.create();
    }
    
    private void initializeUI(View view) {
        bindingsContainer = view.findViewById(R.id.bindings_container);
        CheckBox simultaneousCheckbox = view.findViewById(R.id.checkbox_simultaneous);
        Button addBindingButton = view.findViewById(R.id.btn_add_binding);
        TextView previewText = view.findViewById(R.id.text_preview);
        
        // Загружаем текущие настройки
        simultaneousCheckbox.setChecked(multiBinding.isSimultaneous());
        currentBindings.clear();
        currentBindings.addAll(multiBinding.getBindings());
        
        // Обновляем превью
        updatePreview(previewText);
        
        // Заполняем контейнер привязок
        updateBindingsContainer();
        
        // Обработчик для чекбокса
        simultaneousCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            multiBinding.setSimultaneous(isChecked);
            updatePreview(previewText);
        });
        
        // Кнопка добавления привязки
        addBindingButton.setOnClickListener(v -> {
            if (currentBindings.size() < 6) { // Максимум 6 действий
                currentBindings.add(Binding.NONE);
                updateBindingsContainer();
                updatePreview(previewText);
            }
        });
    }
    
    private void updateBindingsContainer() {
        bindingsContainer.removeAllViews();
        
        for (int i = 0; i < currentBindings.size(); i++) {
            View bindingView = createBindingView(i, currentBindings.get(i));
            bindingsContainer.addView(bindingView);
        }
    }
    
    private View createBindingView(final int index, Binding currentBinding) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.multi_binding_item, bindingsContainer, false);
        
        Spinner bindingSpinner = view.findViewById(R.id.spinner_binding);
        Button removeButton = view.findViewById(R.id.btn_remove);
        
        // Настраиваем спиннер
        List<Binding> availableBindings = getAvailableBindings();
        String[] bindingNames = new String[availableBindings.size()];
        for (int i = 0; i < availableBindings.size(); i++) {
            bindingNames[i] = availableBindings.get(i).toString();
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                bindingNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bindingSpinner.setAdapter(adapter);
        
        // Устанавливаем текущее значение
        int position = availableBindings.indexOf(currentBinding);
        if (position >= 0) {
            bindingSpinner.setSelection(position);
        }
        
        // Обработчик изменения
        bindingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentBindings.set(index, availableBindings.get(position));
                updatePreview(getDialog().findViewById(R.id.text_preview));
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // Кнопка удаления
        removeButton.setOnClickListener(v -> {
            currentBindings.remove(index);
            updateBindingsContainer();
            updatePreview(getDialog().findViewById(R.id.text_preview));
        });
        
        // Скрываем кнопку удаления если это единственная привязка
        if (currentBindings.size() <= 1) {
            removeButton.setVisibility(View.GONE);
        }
        
        return view;
    }
    
    private void updatePreview(TextView previewText) {
        MultiBinding tempBinding = new MultiBinding(currentBindings, multiBinding.isSimultaneous());
        previewText.setText("Preview: " + tempBinding.toString());
    }
    
    private List<Binding> getAvailableBindings() {
        List<Binding> bindings = new ArrayList<>();
        for (Binding binding : Binding.values()) {
            if (binding != Binding.NONE) {
                bindings.add(binding);
            }
        }
        return bindings;
    }
    
    private void saveMultiBinding() {
        multiBinding.clear();
        for (Binding binding : currentBindings) {
            if (binding != Binding.NONE) {
                multiBinding.addBinding(binding);
            }
        }
        
        if (listener != null) {
            listener.onMultiBindingSet(multiBinding);
        }
    }
}
