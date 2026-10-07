package com.ctv.flowtracer.app.ai.repository;

import com.ctv.flowtracer.app.ai.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {}
