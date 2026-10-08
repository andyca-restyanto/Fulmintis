// backend/src/main/java/com/example/app/modules/usertype/service/UserTypeLookupServiceImpl.java
package com.example.app.modules.usertype.service;

import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserTypeLookupServiceImpl implements UserTypeLookupService {

    private final UserTypeRepository userTypeRepository;

    @Override
    public Map<String, String> getLabelsByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashMap<>();
        }

        // 1 query ke database master_data (tabel user_type) untuk SEMUA code sekaligus
        return userTypeRepository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(
                        UserType::getCode,
                        UserType::getLabel,
                        (existing, duplicate) -> existing,
                        HashMap::new
                ));
    }
}
