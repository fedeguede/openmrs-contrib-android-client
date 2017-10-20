/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.mobile.activities.formdisplay;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;
import org.openmrs.mobile.R;
import org.openmrs.mobile.activities.ACBaseFragment;
import org.openmrs.mobile.application.OpenMRS;
import org.openmrs.mobile.bluetooth.BleDefinedUUIDs;
import org.openmrs.mobile.bluetooth.BleWrapper;
import org.openmrs.mobile.bluetooth.BleWrapperUiCallbacks;
import org.openmrs.mobile.bluetooth.PressureMonitorUtils;
import org.openmrs.mobile.bundle.FormFieldsWrapper;
import org.openmrs.mobile.models.Answer;
import org.openmrs.mobile.models.Question;
import org.openmrs.mobile.utilities.ApplicationConstants;
import org.openmrs.mobile.utilities.InputField;
import org.openmrs.mobile.utilities.RangeEditText;
import org.openmrs.mobile.utilities.SelectOneField;
import org.openmrs.mobile.utilities.ToastUtil;

import java.util.ArrayList;
import java.util.List;

public class FormDisplayPageFragment extends ACBaseFragment<FormDisplayContract.Presenter.PagePresenter> implements FormDisplayContract.View.PageView{

    public static final String CONCEPT_BMI_UUID = "cfdcd2e9-79d5-4980-b0e2-d2983b9350fb";
    private List<InputField> inputFields = new ArrayList<>();
    private List<SelectOneField> selectOneFields = new ArrayList<>();
    private LinearLayout mParent;
    public Toast mToast;
    private boolean bReconnect;
    ProgressDialog mPDialog;
    BleWrapper mBleWrapper;
    private static final String TAG_CLASS="FDispPageFrag.java";
    private RangeEditText mEditTextSystolic;
    private RangeEditText mEditTextDiastolic;
    private TextView mTxtView;
    private ArrayList<Integer> mListSystolic,mListDiastolic;
    int mDiastolic,mSystolic;

    public static FormDisplayPageFragment newInstance() {
        return new FormDisplayPageFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_form_display, container, false);
        getActivity().getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        mParent = (LinearLayout) root.findViewById(R.id.sectionContainer);
        mListDiastolic=new ArrayList<Integer>();
        mListSystolic=new ArrayList<Integer>();
        return root;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        FormFieldsWrapper formFieldsWrapper = new FormFieldsWrapper(getInputFields(), getSelectOneFields());
        outState.putSerializable(ApplicationConstants.BundleKeys.FORM_FIELDS_BUNDLE, formFieldsWrapper);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            FormFieldsWrapper formFieldsWrapper = (FormFieldsWrapper) savedInstanceState.getSerializable(ApplicationConstants.BundleKeys.FORM_FIELDS_BUNDLE);
            inputFields = formFieldsWrapper.getInputFields();
            for (InputField field : inputFields) {
                if (field.isRed()) {
                    RangeEditText ed = (RangeEditText) getActivity().findViewById(field.getId());
                    ed.setTextColor(ContextCompat.getColor(OpenMRS.getInstance(), R.color.red));
                }
            }
            selectOneFields = formFieldsWrapper.getSelectOneFields();
        }
    }

    @Override
    public void attachSectionToView(LinearLayout linearLayout) {
        mParent.addView(linearLayout);
    }

    @Override
    public void attachQuestionToSection(LinearLayout section, LinearLayout question) {
        section.addView(question);
    }

    @Override
    public void createAndAttachNumericQuestionEditText(Question question, LinearLayout sectionLinearLayout) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        RangeEditText ed = new RangeEditText(getActivity());
        DiscreteSeekBar dsb = new DiscreteSeekBar(getActivity());
        InputField field = new InputField(question.getQuestionOptions().getConcept());
        InputField inputField = getInputField(field.getConcept());
        if (inputField != null) {
            inputField.setId(field.getId());
        } else {
            field.setConcept(question.getQuestionOptions().getConcept());
            inputFields.add(field);
        }
        sectionLinearLayout.addView(generateTextView(question.getLabel()));
        if (question.getLabel().equalsIgnoreCase("BMI (kg/m2):")) {
            TextView tv = new TextView(getActivity());
            tv.setId(field.getId());
            sectionLinearLayout.addView(tv, layoutParams);
        } else if ((question.getQuestionOptions().getMax() != null) && (!(question.getQuestionOptions().isAllowDecimal()))) {
            dsb.setMax((int) Double.parseDouble(question.getQuestionOptions().getMax()));
            dsb.setMin((int) Double.parseDouble(question.getQuestionOptions().getMin()));
            dsb.setId(field.getId());
            sectionLinearLayout.addView(dsb, layoutParams);
        } else {
            ed.setName(question.getLabel());
            ed.setSingleLine(true);
            ed.setHint(question.getLabel());
            ed.setLowerlimit(-1.0);
            ed.setUpperlimit(-1.0);
            ed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            if (question.getQuestionOptions().isAllowDecimal()) {
                ed.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            } else {
                ed.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
            ed.setId(field.getId());
            sectionLinearLayout.addView(ed, layoutParams);


            if (question.getLabel().contentEquals("Height(cm):") || question.getLabel().contentEquals("Weight(Kg):"))
                ed.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateBMI();
                    }
                });
            if (question.getLabel().contentEquals("BP:Systolic:"))
                mEditTextSystolic=ed;
            if (question.getLabel().contentEquals("BP:Diastolic:")){
                mEditTextDiastolic=ed;
                mTxtView=generateTextView("Number of measurements: 0");
                sectionLinearLayout.addView(mTxtView, layoutParams);


                Log.d(TAG_CLASS, "create Button");
             /*   ImageButton bluetoothButton= new ImageButton(getContext());
                bluetoothButton.setImageDrawable(getResources().getDrawable(R.drawable.ico_vitals_small));*/
                Button bluetoothButton=new Button(getContext());
                bluetoothButton.setText("Get measurement");
                bluetoothButton.setEnabled(true);
                mPDialog= new ProgressDialog(this.getActivity());
                mPDialog.setMessage("Connecting to blood pressure monitor it can take over 6 seconds");
                mPDialog.setIndeterminate(true);
                mPDialog.setCancelable(true);
                mPDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        bReconnect=false;
                        mBleWrapper.stopScanning();
                        mBleWrapper.diconnect();
                        mBleWrapper.close();
                        mPDialog.cancel();
                        bluetoothButton.setEnabled(true);

                    }
                });

                bluetoothButton.setOnClickListener(view->{
                    bluetoothButton.setEnabled(false);
                            mPDialog.show();
                            bReconnect=true;

                            mBleWrapper= new BleWrapper(getContext(), new BleWrapperUiCallbacks() {
                                @Override
                                public void uiDeviceFound(BluetoothDevice device, int rssi, byte[] record) {
                                    Log.d(TAG_CLASS, "uiDeviceFound" + device.getName());
                                    if (device.getName().startsWith("0")) { //ModeNormal
                                        if (PressureMonitorUtils.checkAccountID(getContext(), device.getName())) {
                                            Log.d(TAG_CLASS, "uiDeviceFound registered" + device.getName());
                                            mBleWrapper.stopScanning();
                                            mBleWrapper.connect(device.getAddress());
                                            if (mToast!=null)
                                                mToast.cancel();
                                        } else {
                                            if (mToast!=null)
                                                mToast.cancel();
                                            mToast = Toast.makeText(getContext(), "Device must be paired before transferring data", Toast.LENGTH_SHORT);
                                            mToast.show();
                                            Log.d(TAG_CLASS, "uiDeviceFound not registered" + device.getName());
                                        }
                                    }
                                }

                                @Override
                                public void uiDeviceConnected(BluetoothGatt gatt, BluetoothDevice device) {
                                    Log.d(TAG_CLASS,"uiDeviceConnected"+ device.getName());

                                }

                                @Override
                                public void uiDeviceDisconnected(BluetoothGatt gatt, BluetoothDevice device) {
                                    Log.d(TAG_CLASS,"uiDeviceDisconnected"+ device.getName());
                                    if (bReconnect)
                                        mBleWrapper.connect(device.getAddress());

                                }

                                @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
                                @Override
                                public void uiAvailableServices(BluetoothGatt gatt, BluetoothDevice device, List<BluetoothGattService> services) {
                                    bReconnect=false;
                                    Log.d(TAG_CLASS, "uiAvailableServices name" + device.getName());
                                    Log.d(TAG_CLASS, "getService PPRESS");
                                    String acountID = device.getName().substring(1, 6);
                                    Log.d(TAG_CLASS, "uiAvailableServices account id" + acountID);
                                    BluetoothGattService service = gatt.getService(BleDefinedUUIDs.Service.CUSTOM_SERVICE);
                                    if (service == null)
                                        Log.d(TAG_CLASS, "Could not get CUSTOM_SERVICE Service");
                                    else {
                                        Log.d(TAG_CLASS, "Heart Rate Service successfully retrieved");
                                        mBleWrapper.getCharacteristicsForService(service);
                                    }
                                }

                                @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
                                @Override
                                public void uiCharacteristicForService(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, List<BluetoothGattCharacteristic> chars) {
                                    Log.d(TAG_CLASS, "uiCharacteristicForService");
                                    BluetoothGattCharacteristic btch = service
                                            .getCharacteristic(BleDefinedUUIDs.Characteristic.READ_RANDOM_CHARACTERISITIC);
                                    if (btch == null) {
                                        Log.d(TAG_CLASS,
                                                "Could not find READ_RANDOM_CHARACTERISITIC Characteristic");
                                    } else {
                                        Log.d(TAG_CLASS,
                                                "READ_RANDOM_CHARACTERISITIC retrieved properly");
                                        mBleWrapper.setNotificationForCharacteristic(btch, true);
                                    }
                                }

                                @Override
                                public void uiCharacteristicsDetails(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic characteristic) {
                                    Log.d(TAG_CLASS, "uiCharacteristicsDetails");

                                }

                                @Override
                                public void uiNewValueForCharacteristic(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String strValue, int intValue, byte[] rawValue, String timestamp) {

                                }

                                @Override
                                public void uiNewValueHRForCharacteristic(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String strValue, int intValue, byte[] rawValue, String timestamp) {

                                }

                                @Override
                                public void uiGotNotification(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic characteristic) {

                                }

                                @Override
                                public void uiSuccessfulWrite(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String description) {
                                    Log.d(TAG_CLASS, "uiSuccessfulWrite");
                                }

                                @Override
                                public void uiFailedWrite(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String description) {

                                }

                                @Override
                                public void uiNewRssiAvailable(BluetoothGatt gatt, BluetoothDevice device, int rssi) {

                                }

                                @Override
                                public void uiNewValuePressForCharacteristic(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, int systolic, int diastolic, int hr) {
                                    Log.d(TAG_CLASS,"uiNewValuePressForCharacteristic");
                                    mPDialog.dismiss();
                                    Log.d(TAG_CLASS,"ui systolic: "+systolic+" diastolic: "+diastolic);
                                    mListSystolic.add(systolic);
                                    mListDiastolic.add(diastolic);
                                    mDiastolic=0;
                                    mSystolic=0;
                                    for(int i=0;i<mListDiastolic.size();i++){
                                        mSystolic+=mListSystolic.get(i);
                                        mDiastolic+=mListDiastolic.get(i);
                                    }
                                    mSystolic=mSystolic/mListSystolic.size();
                                    mDiastolic=mDiastolic/mListDiastolic.size();
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d(TAG_CLASS,"ui setEditText measure #: "+mListSystolic.size());
                                            mTxtView.setText("Number of measurements: "+mListSystolic.size());
                                            mEditTextSystolic.setText(String.valueOf(mSystolic));
                                            mEditTextDiastolic.setText(String.valueOf(mDiastolic));
                                            bluetoothButton.setEnabled(true);
                                        }
                                    });


                                }


                            });

                    if ( mBleWrapper.initialize())
                        mBleWrapper.startScanningCustom(true);
                    else
                        mPDialog.cancel();

                });
                sectionLinearLayout.addView(bluetoothButton, layoutParams);

            }
        }

    }

    private TextView generateTextView(String text) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(10, 0, 0, 0);
        TextView textView = new TextView(getActivity());
        textView.setText(text);
        textView.setLayoutParams(layoutParams);
        return textView;
    }

    public InputField getInputField(String concept) {
        for (InputField inputField : inputFields) {
            if (concept.equals(inputField.getConcept())) {
                return inputField;
            }
        }
        return null;
    }

    public SelectOneField getSelectOneField(String concept) {
        for (SelectOneField selectOneField : selectOneFields) {
            if (concept.equals(selectOneField.getConcept())) {
                return selectOneField;
            }
        }
        return null;
    }

    @Override
    public void createAndAttachSelectQuestionDropdown(Question question, LinearLayout sectionLinearLayout) {
        TextView textView = new TextView(getActivity());
        textView.setPadding(20, 0, 0, 0);
        textView.setText(question.getLabel());
        Spinner spinner = (Spinner) getActivity().getLayoutInflater().inflate(R.layout.form_dropdown, null);

        LinearLayout questionLinearLayout = new LinearLayout(getActivity());
        LinearLayout.LayoutParams questionLinearLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        questionLinearLayout.setOrientation(LinearLayout.VERTICAL);
        questionLinearLayoutParams.gravity = Gravity.START;
        questionLinearLayout.setLayoutParams(questionLinearLayoutParams);

        List<String> answerLabels = new ArrayList<>();
        for (Answer answer : question.getQuestionOptions().getAnswers()) {
            answerLabels.add(answer.getLabel());
        }

        SelectOneField spinnerField = new SelectOneField(question.getQuestionOptions().getAnswers(),
                question.getQuestionOptions().getConcept());

        ArrayAdapter arrayAdapter = new ArrayAdapter(getActivity(), android.R.layout.simple_spinner_item, answerLabels);
        spinner.setAdapter(arrayAdapter);

        LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        questionLinearLayout.addView(textView);
        questionLinearLayout.addView(spinner);

        sectionLinearLayout.setLayoutParams(linearLayoutParams);
        sectionLinearLayout.addView(questionLinearLayout);

        SelectOneField selectOneField = getSelectOneField(spinnerField.getConcept());
        if (selectOneField != null) {
            spinner.setSelection(selectOneField.getChosenAnswerPosition());
            setOnItemSelectedListener(spinner, selectOneField);
        } else {
            setOnItemSelectedListener(spinner, spinnerField);
            selectOneFields.add(spinnerField);
        }
    }

    private void setOnItemSelectedListener(Spinner spinner, final SelectOneField spinnerField) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                spinnerField.setAnswer(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                spinnerField.setAnswer(-1);
            }
        });
    }

    @Override
    public void createAndAttachSelectQuestionRadioButton(Question question, LinearLayout sectionLinearLayout) {
        TextView textView = new TextView(getActivity());
        textView.setPadding(20, 0, 0, 0);
        textView.setText(question.getLabel());

        RadioGroup radioGroup = new RadioGroup(getActivity());

        for (Answer answer : question.getQuestionOptions().getAnswers()) {
            RadioButton radioButton = new RadioButton(getActivity());
            radioButton.setText(answer.getLabel());
            radioGroup.addView(radioButton);
        }
        if (question.getQuestionOptions().getAnswers().size() <= 2)
            radioGroup.setOrientation(LinearLayout.HORIZONTAL);

        SelectOneField radioGroupField = new SelectOneField(question.getQuestionOptions().getAnswers(),
                question.getQuestionOptions().getConcept());

        LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        sectionLinearLayout.addView(textView);
        sectionLinearLayout.addView(radioGroup);

        sectionLinearLayout.setLayoutParams(linearLayoutParams);

        SelectOneField selectOneField = getSelectOneField(radioGroupField.getConcept());
        if (selectOneField != null) {
            if (selectOneField.getChosenAnswerPosition() != -1) {
                RadioButton radioButton = (RadioButton) radioGroup.getChildAt(selectOneField.getChosenAnswerPosition());
                radioButton.setChecked(true);
            }
            setOnCheckedChangeListener(radioGroup, selectOneField);
        } else {
            setOnCheckedChangeListener(radioGroup, radioGroupField);
            selectOneFields.add(radioGroupField);
        }
    }

    private void setOnCheckedChangeListener(RadioGroup radioGroup, final SelectOneField radioGroupField) {
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                View radioButton = radioGroup.findViewById(i);
                int idx = radioGroup.indexOfChild(radioButton);
                radioGroupField.setAnswer(idx);
            }
        });
    }

    @Override
    public LinearLayout createQuestionGroupLayout(String questionLabel) {
        LinearLayout questionLinearLayout = new LinearLayout(getActivity());
        LinearLayout.LayoutParams layoutParams = getAndAdjustLinearLayoutParams(questionLinearLayout);

        TextView tv = new TextView(getActivity());
        tv.setText(questionLabel);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(ContextCompat.getColor(getActivity(), R.color.primary));
        questionLinearLayout.addView(tv, layoutParams);

        return questionLinearLayout;
    }

    @Override
    public LinearLayout createSectionLayout(String sectionLabel) {
        LinearLayout sectionLinearLayout = new LinearLayout(getActivity());
        LinearLayout.LayoutParams layoutParams = getAndAdjustLinearLayoutParams(sectionLinearLayout);

        TextView tv = new TextView(getActivity());
        tv.setText(sectionLabel);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTextColor(ContextCompat.getColor(getActivity(), R.color.primary));
        sectionLinearLayout.addView(tv, layoutParams);

        return sectionLinearLayout;
    }

    private LinearLayout.LayoutParams getAndAdjustLinearLayoutParams(LinearLayout linearLayout) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

        linearLayout.setOrientation(LinearLayout.VERTICAL);

        Resources r = getActivity().getResources();
        float pxLeftMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, r.getDisplayMetrics());
        float pxTopMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, r.getDisplayMetrics());
        float pxRightMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, r.getDisplayMetrics());
        float pxBottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, r.getDisplayMetrics());
        layoutParams.setMargins(Math.round(pxLeftMargin), Math.round(pxTopMargin), Math.round(pxRightMargin), Math.round(pxBottomMargin));

        return layoutParams;
    }

    @Override
    public List<SelectOneField> getSelectOneFields() {
        return selectOneFields;
    }

    @Override
    public List<InputField> getInputFields() {
        for (InputField field : inputFields) {
            if (field.getConcept().equalsIgnoreCase(CONCEPT_BMI_UUID)) {
                Double pes = inputFields.get(2).getValue();
                Double altura = inputFields.get(3).getValue();
                Double bmi = pes * 100 * 100 / (altura * altura);
                Log.d("checkinputfieled.java", "check BMI:" + bmi.toString());
                field.setValue(bmi);
            } else {
                try {
                    RangeEditText ed = (RangeEditText) getActivity().findViewById(field.getId());

                    if (!isEmpty(ed)) {
                        field.setValue(Double.parseDouble(ed.getText().toString()));
                        boolean isRed = (ed.getCurrentTextColor() == ContextCompat.getColor(OpenMRS.getInstance(), R.color.red));
                        field.setIsRed(isRed);
                    } else {
                        field.setValue(-1.0);
                    }
                } catch (ClassCastException e) {
                    DiscreteSeekBar dsb = (DiscreteSeekBar) getActivity().findViewById(field.getId());
                    field.setValue((double) dsb.getProgress());
                }
            }
        }

        return inputFields;
    }

    @Override
    public void setInputFields(List<InputField> inputFields) {
        this.inputFields = inputFields;
    }

    @Override
    public void setSelectOneFields(List<SelectOneField> selectOneFields) {
        this.selectOneFields = selectOneFields;
    }

    public boolean checkInputFields() {
        boolean allEmpty = true;
        boolean valid = true;
        int nInputFields = 0;
        for (InputField field : inputFields) {
            if (!field.getConcept().equalsIgnoreCase(CONCEPT_BMI_UUID)) {
                try {
                    RangeEditText ed = (RangeEditText) getActivity().findViewById(field.getId());
                    if (!isEmpty(ed)) {
                        nInputFields++;
                        allEmpty = false;
                        if (ed.getText().toString().charAt(0) != '.') {
                            Double inp = Double.parseDouble(ed.getText().toString());
                            if (ed.getUpperlimit() != -1.0 && ed.getUpperlimit() != -1.0 && (ed.getUpperlimit() < inp || ed.getLowerlimit() > inp)) {
                                ed.setTextColor(ContextCompat.getColor(OpenMRS.getInstance(), R.color.red));
                                valid = false;
                            }
                        } else {
                            ed.setTextColor(ContextCompat.getColor(OpenMRS.getInstance(), R.color.red));
                            valid = false;
                        }
                    }
                } catch (ClassCastException e) {
                    DiscreteSeekBar dsb = (DiscreteSeekBar) getActivity().findViewById(field.getId());
                    if (dsb.getProgress() > dsb.getMin()) {
                        allEmpty = false;
                        nInputFields++;
                    }
                }
            }
        }

        for (SelectOneField radioGroupField : selectOneFields) {
            if (radioGroupField.getChosenAnswer() != null) {
                allEmpty = false;
                nInputFields++;
            }
        }

        if (allEmpty) {
            ToastUtil.error("All fields cannot be empty");

//        if (nInputFields!=inputFields.size()){
//            ToastUtil.error("All fields must be filled");
            return false;
        }
        return valid;
    }

    private boolean isEmpty(EditText etText) {
        return etText.getText().toString().trim().length() == 0;
    }

    private void updateBMI(){
        inputFields=getInputFields();
        for( InputField field:inputFields) {
            if (field.getConcept().equalsIgnoreCase(CONCEPT_BMI_UUID)) {
                Double pes = inputFields.get(2).getValue();
                Double altura = inputFields.get(3).getValue();
                Double bmi = pes * 100 * 100 / (altura * altura);
                TextView tv = (TextView) getActivity().findViewById(field.getId());
                if (pes==-1.0F || altura==-1.0F)
                    tv.setText(new String());
                else {
                    Log.d("checkinputfieled.java", "check BMI:" + bmi.toString());
                    field.setValue(bmi);
                    int aux = (int) (field.getValue().floatValue() * 100);
                    Float valor = aux / 100.f;
                    tv.setText(valor.toString());
                    if (valor < 25)
                        tv.setTextColor(Color.GREEN);
                    else if (valor > 30)
                        tv.setTextColor(Color.RED);
                    else
                        tv.setTextColor(Color.rgb(255, 127, 0));
                }
            }
        }
    }

}
