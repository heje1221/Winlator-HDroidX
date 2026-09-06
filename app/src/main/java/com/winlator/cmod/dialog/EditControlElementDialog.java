package com.winlator.cmod.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.widget.InputControlsView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class EditControlElementDialog extends DialogFragment {
    private static final int PICK_IMAGE_REQUEST = 1;
    private ControlElement element;
    private InputControlsView inputControlsView;
    private OnElementUpdatedListener listener;
    private ImageView iconPreview;
    private Button btnRemoveCustomIcon;
    private String selectedCustomIconPath;

    public interface OnElementUpdatedListener {
        void onElementUpdated(ControlElement element);
    }

    public static EditControlElementDialog newInstance(ControlElement element, InputControlsView inputControlsView) {
        EditControlElementDialog dialog = new EditControlElementDialog();
        dialog.element = element;
        dialog.inputControlsView = inputControlsView;
        return dialog;
    }

    public void setOnElementUpdatedListener(OnElementUpdatedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.edit_control_element_dialog, null);

        initializeUI(view);

        builder.setView(view)
                //.setTitle(R.string.edit_element)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveElementChanges();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                });

        return builder.create();
    }

    private void initializeUI(View view) {
        // Type spinner
        Spinner typeSpinner = view.findViewById(R.id.spinner_type);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                ControlElement.Type.names()
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(element.getType().ordinal());

        // Shape spinner
        Spinner shapeSpinner = view.findViewById(R.id.spinner_shape);
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                ControlElement.Shape.names()
        );
        shapeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shapeSpinner.setAdapter(shapeAdapter);
        shapeSpinner.setSelection(element.getShape().ordinal());

        // Binding spinners
        initializeBindingSpinners(view);

        // Icon selection
        initializeIconSelection(view);

        // Scale
        TextView scaleText = view.findViewById(R.id.text_scale);
        scaleText.setText(String.format("%.1f", element.getScale()));

        // Toggle switch
        View toggleSwitchLayout = view.findViewById(R.id.layout_toggle_switch);
        toggleSwitchLayout.setVisibility(element.getType() == ControlElement.Type.BUTTON ? View.VISIBLE : View.GONE);
    }

    private void initializeBindingSpinners(View view) {
        List<Binding> availableBindings = getAvailableBindings();
        String[] bindingNames = new String[availableBindings.size()];
        for (int i = 0; i < availableBindings.size(); i++) {
            bindingNames[i] = availableBindings.get(i).toString();
        }

        ArrayAdapter<String> bindingAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                bindingNames
        );
        bindingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        for (int i = 0; i < 4; i++) {
            int spinnerId = getResources().getIdentifier("spinner_binding_" + (i + 1), "id", getContext().getPackageName());
            Spinner bindingSpinner = view.findViewById(spinnerId);
            if (bindingSpinner != null) {
                bindingSpinner.setAdapter(bindingAdapter);
                if (i < element.getBindingCount()) {
                    Binding currentBinding = element.getBindingAt(i);
                    int position = availableBindings.indexOf(currentBinding);
                    if (position >= 0) {
                        bindingSpinner.setSelection(position);
                    }
                }
            }
        }
    }

    private void initializeIconSelection(View view) {
        iconPreview = view.findViewById(R.id.icon_preview);
        btnRemoveCustomIcon = view.findViewById(R.id.btn_remove_custom_icon);
        Button btnSelectCustomIcon = view.findViewById(R.id.btn_select_custom_icon);

        // Load current icon
        updateIconPreview();

        btnSelectCustomIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCustomIcon();
            }
        });

        btnRemoveCustomIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeCustomIcon();
            }
        });

        // Update remove button visibility
        updateRemoveButtonVisibility();
    }

    private void updateIconPreview() {
        if (element.hasCustomIcon()) {
            Bitmap customIcon = inputControlsView.getCustomIcon(element.getId());
            if (customIcon != null) {
                iconPreview.setImageBitmap(customIcon);
                return;
            }
        }

        // Fallback to default icon
        if (element.getIconId() > 0) {
            Bitmap defaultIcon = inputControlsView.getIcon(element.getIconId());
            if (defaultIcon != null) {
                iconPreview.setImageBitmap(defaultIcon);
                return;
            }
        }

        // No icon - show placeholder
        iconPreview.setImageResource(R.drawable.ic_no_icon);
    }

    private void updateRemoveButtonVisibility() {
        btnRemoveCustomIcon.setVisibility(element.hasCustomIcon() ? View.VISIBLE : View.GONE);
    }

    private void selectCustomIcon() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Select Custom Icon"),
                PICK_IMAGE_REQUEST
            );
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getContext(), "No file browser available", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeCustomIcon() {
        inputControlsView.removeCustomIcon(element.getId());
        element.setHasCustomIcon(false);
        element.setCustomIconId(null);
        updateIconPreview();
        updateRemoveButtonVisibility();
        Toast.makeText(getContext(), "Custom icon removed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == getActivity().RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri imageUri = data.getData();
                processSelectedImage(imageUri);
            }
        }
    }

    private void processSelectedImage(Uri imageUri) {
        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(imageUri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            
            if (originalBitmap != null) {
                // Resize to appropriate size for icons (e.g., 64x64)
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 64, 64, true);
                
                // Set as custom icon
                inputControlsView.setCustomIcon(element.getId(), resizedBitmap);
                element.setHasCustomIcon(true);
                element.setCustomIconId(element.getId() + "_custom");
                
                updateIconPreview();
                updateRemoveButtonVisibility();
                Toast.makeText(getContext(), "Custom icon set", Toast.LENGTH_SHORT).show();
                
                // Clean up
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error loading image", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
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

    private void saveElementChanges() {
        // Save type
        Spinner typeSpinner = getDialog().findViewById(R.id.spinner_type);
        element.setType(ControlElement.Type.values()[typeSpinner.getSelectedItemPosition()]);

        // Save shape
        Spinner shapeSpinner = getDialog().findViewById(R.id.spinner_shape);
        element.setShape(ControlElement.Shape.values()[shapeSpinner.getSelectedItemPosition()]);

        // Save bindings
        for (int i = 0; i < 4; i++) {
            int spinnerId = getResources().getIdentifier("spinner_binding_" + (i + 1), "id", getContext().getPackageName());
            Spinner bindingSpinner = getDialog().findViewById(spinnerId);
            if (bindingSpinner != null && bindingSpinner.getSelectedItemPosition() >= 0) {
                List<Binding> availableBindings = getAvailableBindings();
                Binding selectedBinding = availableBindings.get(bindingSpinner.getSelectedItemPosition());
                element.setBindingAt(i, selectedBinding);
            }
        }

        // Save scale
        TextView scaleText = getDialog().findViewById(R.id.text_scale);
        try {
            float scale = Float.parseFloat(scaleText.getText().toString());
            element.setScale(Math.max(0.5f, Math.min(3.0f, scale)));
        } catch (NumberFormatException e) {
            // Keep current scale
        }

        if (listener != null) {
            listener.onElementUpdated(element);
        }
    }
}
