import java.util.*;
public class TestParse2 {
    public static int[] nextGreater(int[] nums) {
        int n = nums.length;
        int[] ans = new int[n];
        Stack<Integer> st = new Stack<>();

        // Iterate twice the length of the array to simulate circular behavior
        for (int i = 2 * n - 1; i >= 0; i--) {
            int currentIndex = i % n;

            // Remove elements from stack that are smaller than or equal to current
            while (!st.isEmpty() && st.peek() <= nums[currentIndex]) {
                st.pop();
            }

            // Only update the answer array when we are evaluating the actual elements (i < n)
            if (i < n) {
                if (st.isEmpty()) {
                    ans[currentIndex] = -1;
                } else {
                    ans[currentIndex] = st.peek();
                }
            }

            // Push current element to the stack
            st.push(nums[currentIndex]);
        }

        return ans;
    }
    public static void main(String[] args) {
        int[] result = nextGreater(new int[]{4, 5, 2, 25});
        System.out.println(Arrays.toString(result));
    }
}
