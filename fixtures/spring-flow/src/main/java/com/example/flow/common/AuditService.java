package com.example.flow.common;

import com.acme.lib.BaseService;
import org.springframework.stereotype.Service;

/** Service kế thừa một lớp cơ sở nằm trong thư viện (không có mã nguồn): hàm {@code record} được kế thừa từ đó. */
@Service
public class AuditService extends BaseService {}
